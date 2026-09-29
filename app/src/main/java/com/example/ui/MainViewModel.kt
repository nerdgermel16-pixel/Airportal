package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DeviceStatus
import com.example.data.model.ServerLogEntry
import com.example.data.repository.DeviceStatusProvider
import com.example.server.AirPortalService
import com.example.server.ServerLogManager
import com.example.server.ServerServiceState
import com.example.util.NetworkUtils
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionStatus(
  val permission: String,
  val title: String,
  val description: String,
  val isGranted: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val context: Context get() = getApplication()
  private val deviceStatusProvider = DeviceStatusProvider(context)

  val serviceState: StateFlow<ServerServiceState> = AirPortalService.serverState
  val logs: StateFlow<List<ServerLogEntry>> = ServerLogManager.logs.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
  val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()

  private val _selectedPort = MutableStateFlow(8080)
  val selectedPort: StateFlow<Int> = _selectedPort.asStateFlow()

  private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
  val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

  private val _permissions = MutableStateFlow<List<PermissionStatus>>(emptyList())
  val permissions: StateFlow<List<PermissionStatus>> = _permissions.asStateFlow()

  init {
    refreshNetworkAndStatus()
    checkPermissions()
    viewModelScope.launch {
      serviceState.collect { state ->
        val url = "http://${state.ipAddress}:${state.port}"
        generateQrCode(url)
        refreshDeviceStatus(state.port, state.isRunning)
      }
    }
  }

  fun setPort(port: Int) {
    if (port in 1024..65535) {
      _selectedPort.value = port
      if (!serviceState.value.isRunning) {
        val ip = NetworkUtils.getLocalIpAddress(context)
        generateQrCode("http://$ip:$port")
      }
    }
  }

  fun toggleServer() {
    if (serviceState.value.isRunning) {
      stopServer()
    } else {
      startServer()
    }
  }

  fun startServer() {
    AirPortalService.startService(context, _selectedPort.value)
  }

  fun stopServer() {
    AirPortalService.stopService(context)
  }

  fun clearLogs() {
    ServerLogManager.clearLogs()
  }

  fun refreshNetworkAndStatus() {
    viewModelScope.launch {
      val ip = withContext(Dispatchers.IO) {
        NetworkUtils.getLocalIpAddress(context)
      }
      val port = if (serviceState.value.isRunning) serviceState.value.port else _selectedPort.value
      val url = "http://$ip:$port"
      generateQrCode(url)
      refreshDeviceStatus(port, serviceState.value.isRunning)
      checkPermissions()
    }
  }

  private fun refreshDeviceStatus(port: Int, isRunning: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      val status = deviceStatusProvider.getDeviceStatus(port, isRunning)
      _deviceStatus.value = status
    }
  }

  private fun generateQrCode(url: String) {
    viewModelScope.launch(Dispatchers.Default) {
      val bitmap = QrCodeGenerator.generateQrBitmap(url, 400)
      _qrBitmap.value = bitmap
    }
  }

  fun checkPermissions() {
    val list = mutableListOf<PermissionStatus>()

    // SMS
    list.add(
      PermissionStatus(
        permission = android.Manifest.permission.READ_SMS,
        title = "Read SMS",
        description = "Allows viewing SMS message threads in the web dashboard",
        isGranted = hasPermission(android.Manifest.permission.READ_SMS)
      )
    )
    list.add(
      PermissionStatus(
        permission = android.Manifest.permission.SEND_SMS,
        title = "Send SMS",
        description = "Allows sending new SMS messages directly from your PC browser",
        isGranted = hasPermission(android.Manifest.permission.SEND_SMS)
      )
    )

    // Contacts & Calls
    list.add(
      PermissionStatus(
        permission = android.Manifest.permission.READ_CONTACTS,
        title = "Read Contacts",
        description = "Displays phonebook contacts and resolves sender names",
        isGranted = hasPermission(android.Manifest.permission.READ_CONTACTS)
      )
    )
    list.add(
      PermissionStatus(
        permission = android.Manifest.permission.READ_CALL_LOG,
        title = "Call Logs",
        description = "Displays incoming, outgoing, and missed call history",
        isGranted = hasPermission(android.Manifest.permission.READ_CALL_LOG)
      )
    )

    // Storage / Media
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      list.add(
        PermissionStatus(
          permission = android.Manifest.permission.READ_MEDIA_IMAGES,
          title = "Photos & Media",
          description = "Allows browsing and previewing photo gallery from PC",
          isGranted = hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
        )
      )
      list.add(
        PermissionStatus(
          permission = android.Manifest.permission.READ_MEDIA_AUDIO,
          title = "Music & Audio",
          description = "Allows playing and streaming music tracks from phone",
          isGranted = hasPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        )
      )
      list.add(
        PermissionStatus(
          permission = android.Manifest.permission.POST_NOTIFICATIONS,
          title = "Notifications",
          description = "Required to keep the server running in background",
          isGranted = hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        )
      )
    } else {
      list.add(
        PermissionStatus(
          permission = android.Manifest.permission.READ_EXTERNAL_STORAGE,
          title = "Storage Access",
          description = "Allows browsing files and downloading/uploading documents",
          isGranted = hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        )
      )
    }

    _permissions.value = list
  }

  private fun hasPermission(perm: String): Boolean {
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
  }
}
