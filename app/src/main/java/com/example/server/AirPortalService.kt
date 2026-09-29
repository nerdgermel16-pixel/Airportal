package com.example.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.AirPortalApp
import com.example.MainActivity
import com.example.R
import com.example.data.repository.ContactsRepository
import com.example.data.repository.DeviceStatusProvider
import com.example.data.repository.FileRepository
import com.example.data.repository.SmsRepository
import com.example.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerServiceState(
  val isRunning: Boolean = false,
  val ipAddress: String = "127.0.0.1",
  val port: Int = 8080,
  val error: String? = null
)

class AirPortalService : Service() {

  companion object {
    const val ACTION_START = "com.example.action.START_SERVER"
    const val ACTION_STOP = "com.example.action.STOP_SERVER"
    const val EXTRA_PORT = "com.example.extra.PORT"
    const val NOTIFICATION_ID = 1001

    private val _serverState = MutableStateFlow(ServerServiceState())
    val serverState: StateFlow<ServerServiceState> = _serverState.asStateFlow()

    fun startService(context: Context, port: Int = 8080) {
      val intent = Intent(context, AirPortalService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_PORT, port)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, AirPortalService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }

  private var httpServer: AirPortalHttpServer? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val port = intent.getIntExtra(EXTRA_PORT, 8080)
        startServer(port)
      }
      ACTION_STOP -> {
        stopServer()
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun startServer(port: Int) {
    if (httpServer != null && httpServer!!.isAlive) {
      return
    }

    val ip = NetworkUtils.getLocalIpAddress(this)

    try {
      acquireLocks()

      val contactsRepo = ContactsRepository(applicationContext)
      val smsRepo = SmsRepository(applicationContext, contactsRepo)
      val fileRepo = FileRepository(applicationContext)
      val deviceStatusProvider = DeviceStatusProvider(applicationContext)

      val server = AirPortalHttpServer(
        port = port,
        context = applicationContext,
        smsRepo = smsRepo,
        contactsRepo = contactsRepo,
        fileRepo = fileRepo,
        deviceStatusProvider = deviceStatusProvider
      )

      server.start()
      httpServer = server

      val notification = buildNotification(ip, port)
      startForeground(NOTIFICATION_ID, notification)

      _serverState.value = ServerServiceState(
        isRunning = true,
        ipAddress = ip,
        port = port,
        error = null
      )

      ServerLogManager.addLog(
        method = "SYSTEM",
        uri = "SERVER_START",
        statusCode = 200,
        clientIp = "local",
        details = "Server started on http://$ip:$port"
      )
    } catch (e: Exception) {
      releaseLocks()
      _serverState.value = ServerServiceState(
        isRunning = false,
        ipAddress = ip,
        port = port,
        error = e.message ?: "Failed to bind port $port"
      )
      stopSelf()
    }
  }

  private fun stopServer() {
    try {
      httpServer?.stop()
      httpServer = null
      releaseLocks()

      ServerLogManager.addLog(
        method = "SYSTEM",
        uri = "SERVER_STOP",
        statusCode = 200,
        clientIp = "local",
        details = "Server stopped"
      )
    } catch (_: Exception) {
    } finally {
      val ip = NetworkUtils.getLocalIpAddress(this)
      val port = _serverState.value.port
      _serverState.value = ServerServiceState(
        isRunning = false,
        ipAddress = ip,
        port = port,
        error = null
      )
    }
  }

  private fun buildNotification(ip: String, port: Int): Notification {
    val openIntent = Intent(this, MainActivity::class.java)
    val openPendingIntent = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val stopIntent = Intent(this, AirPortalService::class.java).apply {
      action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
      this,
      1,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val serverUrl = "http://$ip:$port"

    return NotificationCompat.Builder(this, AirPortalApp.CHANNEL_ID)
      .setContentTitle(getString(R.string.notification_title))
      .setContentText("Accessible at $serverUrl")
      .setSmallIcon(R.drawable.airportal_icon_1790584948858)
      .setOngoing(true)
      .setContentIntent(openPendingIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun acquireLocks() {
    try {
      val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
      wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirPortal:ServerWakeLock")?.apply {
        acquire(24 * 60 * 60 * 1000L) // 24 hours max
      }

      val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AirPortal:WifiLock")?.apply {
        acquire()
      }
    } catch (_: Exception) {
    }
  }

  private fun releaseLocks() {
    try {
      if (wakeLock?.isHeld == true) wakeLock?.release()
    } catch (_: Exception) {
    }
    wakeLock = null

    try {
      if (wifiLock?.isHeld == true) wifiLock?.release()
    } catch (_: Exception) {
    }
    wifiLock = null
  }

  override fun onDestroy() {
    stopServer()
    super.onDestroy()
  }
}
