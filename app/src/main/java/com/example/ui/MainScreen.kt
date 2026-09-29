package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val serviceState by viewModel.serviceState.collectAsState()
  val deviceStatus by viewModel.deviceStatus.collectAsState()
  val logs by viewModel.logs.collectAsState()
  val qrBitmap by viewModel.qrBitmap.collectAsState()
  val permissions by viewModel.permissions.collectAsState()
  val selectedPort by viewModel.selectedPort.collectAsState()

  var selectedTab by remember { mutableIntStateOf(0) }
  var showQrDialog by remember { mutableStateOf(false) }

  // Multi-permission launcher
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {
    viewModel.checkPermissions()
  }

  val serverUrl = "http://${serviceState.ipAddress}:${serviceState.port}"

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "AirPortal",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Wireless Device Manager",
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = {
              viewModel.refreshNetworkAndStatus()
              Toast.makeText(context, "Network info refreshed", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.testTag("refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Server Status Banner Card
      ServerHeroCard(
        isRunning = serviceState.isRunning,
        serverUrl = serverUrl,
        error = serviceState.error,
        onToggleServer = { viewModel.toggleServer() },
        onCopyUrl = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText("AirPortal URL", serverUrl))
          Toast.makeText(context, "Copied URL: $serverUrl", Toast.LENGTH_SHORT).show()
        },
        onOpenInBrowser = {
          try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
            context.startActivity(intent)
          } catch (e: Exception) {
            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
          }
        },
        onShareUrl = {
          val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Access my device on AirPortal at: $serverUrl")
            type = "text/plain"
          }
          context.startActivity(Intent.createChooser(sendIntent, "Share AirPortal Link"))
        },
        onShowQr = { showQrDialog = !showQrDialog }
      )

      // Optional QR Code Collapsible Display
      AnimatedVisibility(visible = showQrDialog) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Scan to Open Web Dashboard",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Connect PC or other devices to the same Wi-Fi network",
              style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(bottom = 12.dp)
            )
            qrBitmap?.let { bitmap ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color.White)
                  .padding(8.dp)
              ) {
                Image(
                  bitmap = bitmap.asImageBitmap(),
                  contentDescription = "AirPortal QR Code",
                  modifier = Modifier.size(180.dp)
                )
              }
            }
            Text(
              text = serverUrl,
              style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
              ),
              modifier = Modifier.padding(top = 10.dp)
            )
          }
        }
      }

      // Secondary tabs
      SecondaryTabRow(
        selectedTabIndex = selectedTab,
        modifier = Modifier.fillMaxWidth()
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Overview") }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Permissions (${permissions.count { it.isGranted }}/${permissions.size})") }
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Live Logs (${logs.size})") }
        )
      }

      when (selectedTab) {
        0 -> OverviewTabContent(
          deviceStatus = deviceStatus,
          selectedPort = selectedPort,
          isRunning = serviceState.isRunning,
          onPortChanged = { viewModel.setPort(it) },
          onOpenPermissions = { selectedTab = 1 }
        )
        1 -> PermissionsTabContent(
          permissions = permissions,
          onRequestPermissions = {
            val ungranted = permissions.filter { !it.isGranted }.map { it.permission }.toTypedArray()
            if (ungranted.isNotEmpty()) {
              permissionLauncher.launch(ungranted)
            } else {
              Toast.makeText(context, "All required permissions are already granted!", Toast.LENGTH_SHORT).show()
            }
          }
        )
        2 -> LogsTabContent(
          logs = logs,
          onClearLogs = { viewModel.clearLogs() }
        )
      }
    }
  }
}

@Composable
fun ServerHeroCard(
  isRunning: Boolean,
  serverUrl: String,
  error: String?,
  onToggleServer: () -> Unit,
  onCopyUrl: () -> Unit,
  onOpenInBrowser: () -> Unit,
  onShareUrl: () -> Unit,
  onShowQr: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  val statusColor by animateColorAsState(
    targetValue = if (isRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
    label = "statusColor"
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(12.dp)
              .scale(if (isRunning) pulseScale else 1f)
              .clip(CircleShape)
              .background(statusColor)
          )
          Text(
            text = if (isRunning) "SERVER ACTIVE" else "SERVER OFFLINE",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = statusColor
            )
          )
        }

        IconButton(onClick = onShowQr) {
          Icon(Icons.Default.QrCode2, contentDescription = "Toggle QR Code", tint = MaterialTheme.colorScheme.primary)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = if (isRunning) "Web Dashboard URL" else "Local Address",
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )

      Text(
        text = serverUrl,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.ExtraBold,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp
        ),
        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
      )

      if (error != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
          Text(
            text = error,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Action buttons row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onCopyUrl,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onOpenInBrowser,
          enabled = isRunning,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Open", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onShareUrl,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Share", fontSize = 12.sp)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Main Toggle Button
      Button(
        onClick = onToggleServer,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("toggle_server_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(
          imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
          contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isRunning) "STOP SERVER" else "START SERVER",
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
      }
    }
  }
}

@Composable
fun OverviewTabContent(
  deviceStatus: com.example.data.model.DeviceStatus?,
  selectedPort: Int,
  isRunning: Boolean,
  onPortChanged: (Int) -> Unit,
  onOpenPermissions: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Device Stats Cards
    item {
      Text(
        text = "Device Status",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          StatusRowItem(
            label = "Device",
            value = deviceStatus?.deviceName ?: "Android Device",
            icon = Icons.Default.PlayArrow
          )
          StatusRowItem(
            label = "Wi-Fi Network",
            value = deviceStatus?.wifiSsid ?: "Connected",
            icon = Icons.Default.Wifi
          )
          StatusRowItem(
            label = "Battery",
            value = "${deviceStatus?.batteryLevel ?: 0}% ${if (deviceStatus?.isCharging == true) "(Charging)" else ""}",
            icon = Icons.Default.BatteryChargingFull
          )
          val totalGb = ((deviceStatus?.totalStorageBytes ?: 0L) / (1024 * 1024 * 1024.0))
          val freeGb = ((deviceStatus?.availableStorageBytes ?: 0L) / (1024 * 1024 * 1024.0))
          StatusRowItem(
            label = "Storage Free",
            value = String.format(Locale.getDefault(), "%.1f GB free of %.1f GB", freeGb, totalGb),
            icon = Icons.Default.Folder
          )
        }
      }
    }

    // Server Port Settings
    item {
      Text(
        text = "Server Configuration",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Text(
            text = "Listening Port",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
          )
          Text(
            text = "Choose a local port (Stop server to change)",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(bottom = 10.dp)
          )

          val ports = listOf(8080, 8888, 9090, 8000)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            ports.forEach { port ->
              val isSelected = selectedPort == port
              OutlinedButton(
                onClick = { onPortChanged(port) },
                enabled = !isRunning,
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = port.toString(),
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }
    }

    // Quick Guide
    item {
      Text(
        text = "How to Connect from your Computer",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          GuideStepItem(number = "1", text = "Ensure your phone and computer are on the same Wi-Fi network.")
          GuideStepItem(number = "2", text = "Tap 'START SERVER' above to activate the embedded HTTP server.")
          GuideStepItem(number = "3", text = "Open Chrome, Safari, or Edge on your PC and enter the URL shown above.")
          GuideStepItem(number = "4", text = "Enjoy instant access to SMS conversations, Contacts, and Files!")
        }
      }
    }
  }
}

@Composable
fun GuideStepItem(number: String, text: String) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = number,
        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
fun StatusRowItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )
    }
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    )
  }
}

@Composable
fun PermissionsTabContent(
  permissions: List<PermissionStatus>,
  onRequestPermissions: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Text(
            text = "Device Access Permissions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "AirPortal accesses native SMS, Contacts, and Files on your phone to display and manage them on your PC browser.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
          )
          Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Required Permissions")
          }
        }
      }
    }

    items(permissions) { perm ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = if (perm.isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = perm.title,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = perm.description,
              style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
          }

          if (perm.isGranted) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF10B981))
              Text("Granted", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.Warning, contentDescription = "Missing", tint = MaterialTheme.colorScheme.error)
              Text("Missing", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
fun LogsTabContent(
  logs: List<ServerLogEntry>,
  onClearLogs: () -> Unit
) {
  val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "HTTP Activity Log",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
      IconButton(onClick = onClearLogs, enabled = logs.isNotEmpty()) {
        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
      }
    }

    if (logs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No connection requests yet.\nStart the server and visit the URL to see live requests.",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(logs, key = { it.id }) { log ->
          val methodBg = when (log.method) {
            "GET" -> Color(0xFF0284C7)
            "POST" -> Color(0xFF10B981)
            "DELETE" -> Color(0xFFEF4444)
            "SYSTEM" -> Color(0xFF8B5CF6)
            else -> Color(0xFF64748B)
          }

          val statusBg = when {
            log.statusCode in 200..299 -> Color(0xFF10B981)
            log.statusCode in 400..499 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
          }

          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(methodBg)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = log.method,
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                  )
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = log.uri,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "From ${log.clientIp} • ${dateFormat.format(Date(log.timestamp))}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                )
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(statusBg.copy(alpha = 0.2f))
                  .border(1.dp, statusBg, RoundedCornerShape(4.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = log.statusCode.toString(),
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = statusBg,
                    fontWeight = FontWeight.Bold
                  )
                )
              }
            }
          }
        }
      }
    }
  }
}
