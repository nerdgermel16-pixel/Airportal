package com.example.data.model

data class SmsMessageItem(
  val id: Long,
  val threadId: Long,
  val address: String,
  val contactName: String?,
  val body: String,
  val date: Long,
  val type: Int, // 1 = Inbox, 2 = Sent
  val read: Int = 1 // 0 = unread, 1 = read
)

data class SmsThreadItem(
  val threadId: Long,
  val address: String,
  val contactName: String?,
  val snippet: String,
  val date: Long,
  val messageCount: Int,
  val unreadCount: Int = 0
)

data class ContactItem(
  val id: String,
  val displayName: String,
  val primaryNumber: String,
  val numbers: List<String> = emptyList(),
  val photoUri: String? = null
)

data class FileItem(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val size: Long,
  val lastModified: Long,
  val mimeType: String,
  val isImage: Boolean
)

data class DeviceStatus(
  val deviceName: String,
  val model: String,
  val manufacturer: String,
  val androidVersion: String,
  val batteryLevel: Int,
  val isCharging: Boolean,
  val totalStorageBytes: Long,
  val availableStorageBytes: Long,
  val totalRamBytes: Long = 0L,
  val availableRamBytes: Long = 0L,
  val ipAddress: String,
  val port: Int,
  val isRunning: Boolean,
  val wifiSsid: String,
  val batteryTemperature: Float = 32.4f,
  val batteryVoltage: Float = 4.18f,
  val batteryHealth: String = "Good",
  val networkSpeed: String = "17.7 MB/s",
  val simStatus: String = "5G Active"
)

data class AudioTrackItem(
  val id: Long,
  val title: String,
  val artist: String,
  val album: String,
  val duration: Long,
  val size: Long,
  val path: String
)

data class CallLogItem(
  val id: Long,
  val number: String,
  val name: String?,
  val type: Int, // 1 = Incoming, 2 = Outgoing, 3 = Missed
  val date: Long,
  val duration: Long
)

data class ServerLogEntry(
  val id: String,
  val timestamp: Long,
  val method: String,
  val uri: String,
  val statusCode: Int,
  val clientIp: String,
  val details: String = ""
)
