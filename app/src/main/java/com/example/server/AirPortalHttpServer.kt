package com.example.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.data.model.AudioTrackItem
import com.example.data.repository.AudioRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.DeviceStatusProvider
import com.example.data.repository.FileRepository
import com.example.data.repository.SmsRepository
import com.example.ui.web.DashboardHtml
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.media.RingtoneManager
import android.media.Ringtone

class AirPortalHttpServer(
  val port: Int,
  private val context: Context,
  private val smsRepo: SmsRepository,
  private val contactsRepo: ContactsRepository,
  private val fileRepo: FileRepository,
  private val deviceStatusProvider: DeviceStatusProvider
) : NanoHTTPD(port) {

  private val callLogRepo = CallLogRepository(context, contactsRepo)
  private val audioRepo = AudioRepository(context)
  private val mainHandler = Handler(Looper.getMainLooper())
  private var activeRingtone: Ringtone? = null

  override fun serve(session: IHTTPSession): Response {
    val method = session.method
    val uri = session.uri
    val clientIp = session.remoteIpAddress ?: "unknown"

    // Handle CORS preflight
    if (method == Method.OPTIONS) {
      val resp = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
      addCorsHeaders(resp)
      return resp
    }

    val response = try {
      when {
        uri == "/" || uri == "/index.html" -> serveDashboard()
        uri == "/api/status" -> serveStatus()
        uri == "/api/sms" && method == Method.GET -> serveSmsThreads()
        uri == "/api/sms/thread" && method == Method.GET -> serveSmsThreadMessages(session)
        uri == "/api/sms/mark-read" && method == Method.POST -> handleMarkThreadRead(session)
        uri == "/api/sms/thread" && method == Method.DELETE -> handleDeleteSmsThread(session)
        uri == "/api/sms/delete-threads" && method == Method.POST -> handleDeleteMultipleSmsThreads(session)
        uri == "/api/sms/send" && method == Method.POST -> handleSendSms(session)
        uri == "/api/contacts" && method == Method.GET -> serveContacts(session)
        uri == "/api/call-logs" && method == Method.GET -> serveCallLogs()
        uri == "/api/audio" && method == Method.GET -> serveAudioTracks()
        uri == "/api/device/ring" && method == Method.POST -> handleRingDevice()
        uri == "/api/files" && method == Method.GET -> serveFiles(session)
        uri == "/api/files/download" && method == Method.GET -> serveFileDownload(session)
        uri == "/api/files/preview" && method == Method.GET -> serveFilePreview(session)
        uri == "/api/files/upload" && method == Method.POST -> handleFileUpload(session)
        uri == "/api/files/delete" && method == Method.DELETE -> handleDeleteFile(session)
        uri == "/api/clipboard" && method == Method.GET -> serveClipboard()
        uri == "/api/clipboard" && method == Method.POST -> handleSetClipboard(session)
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
      }
    } catch (e: Exception) {
      val errJson = JSONObject().apply {
        put("success", false)
        put("error", e.message ?: "Internal Server Error")
      }
      newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", errJson.toString())
    }

    addCorsHeaders(response)

    // Log request for in-app connection feed
    ServerLogManager.addLog(
      method = method.name,
      uri = uri,
      statusCode = response.status.requestStatus,
      clientIp = clientIp,
      details = session.queryParameterString ?: ""
    )

    return response
  }

  private fun addCorsHeaders(response: Response) {
    response.addHeader("Access-Control-Allow-Origin", "*")
    response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
    response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
  }

  private fun serveDashboard(): Response {
    val html = DashboardHtml.getHtml(context)
    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
  }

  private fun serveStatus(): Response {
    val status = deviceStatusProvider.getDeviceStatus(port, isRunning = true)
    val json = JSONObject().apply {
      put("deviceName", status.deviceName)
      put("model", status.model)
      put("manufacturer", status.manufacturer)
      put("androidVersion", status.androidVersion)
      put("batteryLevel", status.batteryLevel)
      put("isCharging", status.isCharging)
      put("totalStorageBytes", status.totalStorageBytes)
      put("availableStorageBytes", status.availableStorageBytes)
      put("totalRamBytes", status.totalRamBytes)
      put("availableRamBytes", status.availableRamBytes)
      put("ipAddress", status.ipAddress)
      put("port", status.port)
      put("wifiSsid", status.wifiSsid)
      put("batteryTemperature", status.batteryTemperature)
      put("batteryVoltage", status.batteryVoltage)
      put("batteryHealth", status.batteryHealth)
      put("networkSpeed", status.networkSpeed)
      put("simStatus", status.simStatus)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
  }

  private fun serveAudioTracks(): Response {
    val tracks = audioRepo.getAllAudioTracks()
    val array = JSONArray()
    for (t in tracks) {
      val item = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("artist", t.artist)
        put("album", t.album)
        put("duration", t.duration)
        put("size", t.size)
        put("path", t.path)
      }
      array.put(item)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
  }

  private fun handleRingDevice(): Response {
    mainHandler.post {
      try {
        activeRingtone?.stop()
        val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val rt = RingtoneManager.getRingtone(context, alertUri)
        activeRingtone = rt
        rt.play()
        mainHandler.postDelayed({
          try {
            rt.stop()
            if (activeRingtone == rt) activeRingtone = null
          } catch (_: Exception) {}
        }, 5000)
      } catch (_: Exception) {
      }
    }
    val resJson = JSONObject().apply {
      put("success", true)
      put("message", "Device ring initiated (5 seconds)")
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun serveCallLogs(): Response {
    val logs = callLogRepo.getCallLogs(60)
    val array = JSONArray()
    for (l in logs) {
      val item = JSONObject().apply {
        put("id", l.id)
        put("number", l.number)
        put("name", l.name ?: "")
        put("type", l.type)
        put("date", l.date)
        put("duration", l.duration)
      }
      array.put(item)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
  }

  private fun serveSmsThreads(): Response {
    val threads = smsRepo.getThreads()
    val array = JSONArray()
    for (th in threads) {
      val item = JSONObject().apply {
        put("threadId", th.threadId)
        put("address", th.address)
        put("contactName", th.contactName ?: "")
        put("snippet", th.snippet)
        put("date", th.date)
        put("messageCount", th.messageCount)
        put("unreadCount", th.unreadCount)
      }
      array.put(item)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
  }

  private fun serveSmsThreadMessages(session: IHTTPSession): Response {
    val params = session.parameters
    val threadIdStr = params["threadId"]?.firstOrNull()
    val threadId = threadIdStr?.toLongOrNull() ?: 0L
    val messages = smsRepo.getMessagesForThread(threadId)
    val array = JSONArray()
    for (m in messages) {
      val item = JSONObject().apply {
        put("id", m.id)
        put("threadId", m.threadId)
        put("address", m.address)
        put("contactName", m.contactName ?: "")
        put("body", m.body)
        put("date", m.date)
        put("type", m.type)
        put("read", m.read)
      }
      array.put(item)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
  }

  private fun handleMarkThreadRead(session: IHTTPSession): Response {
    val map = HashMap<String, String>()
    session.parseBody(map)
    val postData = map["postData"] ?: ""
    val json = if (postData.isNotBlank()) JSONObject(postData) else JSONObject()
    val threadId = json.optLong("threadId", 0L)
    if (threadId > 0) {
      smsRepo.markThreadAsRead(threadId)
    }
    val resJson = JSONObject().apply {
      put("success", true)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun handleSendSms(session: IHTTPSession): Response {
    val map = HashMap<String, String>()
    session.parseBody(map)
    val postData = map["postData"] ?: ""
    val json = if (postData.isNotBlank()) JSONObject(postData) else JSONObject()
    val rawTo = json.optString("to", "")
    val message = json.optString("message", "")

    val recipientsList = mutableListOf<String>()
    if (json.has("recipients")) {
      val arr = json.optJSONArray("recipients")
      if (arr != null) {
        for (i in 0 until arr.length()) {
          val num = arr.optString(i).trim()
          if (num.isNotEmpty() && !recipientsList.contains(num)) {
            recipientsList.add(num)
          }
        }
      }
    }

    if (recipientsList.isEmpty() && rawTo.isNotBlank()) {
      val split = rawTo.split(Regex("[\\s,;\\n\\r\\t]+")).filter { it.isNotBlank() }
      for (s in split) {
        val clean = s.trim()
        if (clean.isNotEmpty() && !recipientsList.contains(clean)) {
          recipientsList.add(clean)
        }
      }
    }

    if (recipientsList.isEmpty()) {
      val resJson = JSONObject().apply {
        put("success", false)
        put("error", "No recipient phone number provided")
      }
      return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", resJson.toString())
    }

    var sentCount = 0
    var lastError: String? = null
    val resultsArray = JSONArray()

    for (recipient in recipientsList) {
      val result = smsRepo.sendSms(recipient, message)
      val item = JSONObject().apply {
        put("to", recipient)
        put("success", result.isSuccess)
        if (result.isFailure) {
          put("error", result.exceptionOrNull()?.message ?: "Failed")
          lastError = result.exceptionOrNull()?.message
        }
      }
      resultsArray.put(item)
      if (result.isSuccess) sentCount++
    }

    val resJson = JSONObject().apply {
      put("success", sentCount > 0)
      put("sentCount", sentCount)
      put("totalRecipients", recipientsList.size)
      put("results", resultsArray)
      if (sentCount == 0) {
        put("error", lastError ?: "Failed to send SMS")
      } else if (sentCount < recipientsList.size) {
        put("message", "Sent $sentCount of ${recipientsList.size} messages")
      } else {
        put("message", if (recipientsList.size == 1) "Message sent to ${recipientsList[0]}" else "Sent messages to all ${recipientsList.size} recipients")
      }
    }

    val status = if (sentCount > 0) Response.Status.OK else Response.Status.BAD_REQUEST
    return newFixedLengthResponse(status, "application/json", resJson.toString())
  }

  private fun handleDeleteSmsThread(session: IHTTPSession): Response {
    val threadIdStr = session.parameters["threadId"]?.firstOrNull()
    val threadId = threadIdStr?.toLongOrNull() ?: 0L
    val success = smsRepo.deleteThread(threadId)
    val resJson = JSONObject().apply {
      put("success", success)
      put("threadId", threadId)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun handleDeleteMultipleSmsThreads(session: IHTTPSession): Response {
    val map = HashMap<String, String>()
    session.parseBody(map)
    val postData = map["postData"] ?: ""
    val json = if (postData.isNotBlank()) JSONObject(postData) else JSONObject()
    val arr = json.optJSONArray("threadIds")
    val idList = mutableListOf<Long>()
    if (arr != null) {
      for (i in 0 until arr.length()) {
        val id = arr.optLong(i, 0L)
        if (id > 0) idList.add(id)
      }
    }
    val deletedCount = smsRepo.deleteThreads(idList)
    val resJson = JSONObject().apply {
      put("success", true)
      put("deletedCount", deletedCount)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun serveContacts(session: IHTTPSession): Response {
    val query = session.parameters["q"]?.firstOrNull()
    val contacts = contactsRepo.getContacts(query)
    val array = JSONArray()
    for (c in contacts) {
      val obj = JSONObject().apply {
        put("id", c.id)
        put("displayName", c.displayName)
        put("primaryNumber", c.primaryNumber)
        val nums = JSONArray()
        c.numbers.forEach { nums.put(it) }
        put("numbers", nums)
      }
      array.put(obj)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
  }

  private fun serveFiles(session: IHTTPSession): Response {
    val requestedPath = session.parameters["path"]?.firstOrNull()
    val (currentPath, files) = fileRepo.listFiles(requestedPath)

    val array = JSONArray()
    for (f in files) {
      val item = JSONObject().apply {
        put("name", f.name)
        put("path", f.path)
        put("isDirectory", f.isDirectory)
        put("size", f.size)
        put("lastModified", f.lastModified)
        put("mimeType", f.mimeType)
        put("isImage", f.isImage)
      }
      array.put(item)
    }

    val volumes = fileRepo.getStorageVolumes()
    val volumesArray = JSONArray()
    for (v in volumes) {
      val name = when {
        v.path.contains("emulated") -> "Internal Shared Storage"
        v.path.contains("sdcard") || v.path.matches(Regex(".*/[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")) -> "SD Card (${v.name})"
        else -> "Storage (${v.name})"
      }
      val item = JSONObject().apply {
        put("name", name)
        put("path", v.path)
        put("isDirectory", true)
      }
      volumesArray.put(item)
    }

    val resJson = JSONObject().apply {
      put("currentPath", currentPath)
      put("files", array)
      put("volumes", volumesArray)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun serveFileDownload(session: IHTTPSession): Response {
    val path = session.parameters["path"]?.firstOrNull() ?: ""
    val file = fileRepo.getFile(path)
    if (file == null || !file.exists() || file.isDirectory) {
      return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
    }

    val mime = fileRepo.getMimeType(file)
    val fis = FileInputStream(file)
    val resp = newFixedLengthResponse(Response.Status.OK, mime, fis, file.length())
    resp.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
    return resp
  }

  private fun serveFilePreview(session: IHTTPSession): Response {
    val path = session.parameters["path"]?.firstOrNull() ?: ""
    val file = fileRepo.getFile(path)
    if (file == null || !file.exists() || file.isDirectory) {
      return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    val mime = fileRepo.getMimeType(file)
    val fis = FileInputStream(file)
    val resp = newFixedLengthResponse(Response.Status.OK, mime, fis, file.length())
    resp.addHeader("Cache-Control", "max-age=3600")
    return resp
  }

  private fun handleFileUpload(session: IHTTPSession): Response {
    val targetDir = session.parameters["path"]?.firstOrNull() ?: ""
    val files = HashMap<String, String>()
    session.parseBody(files)

    val params = session.parameters
    var uploadedCount = 0

    // NanoHTTPD places temporary file in files map
    for ((paramKey, tempFilePath) in files) {
      if (paramKey == "postData") continue
      val tempFile = File(tempFilePath)
      if (tempFile.exists()) {
        // The original filename is in session.parameters
        val originalName = params[paramKey]?.firstOrNull() ?: tempFile.name
        val saveResult = fileRepo.saveUploadedFile(targetDir, originalName, FileInputStream(tempFile))
        if (saveResult.isSuccess) {
          uploadedCount++
        }
        tempFile.delete()
      }
    }

    val resJson = JSONObject().apply {
      put("success", true)
      put("uploadedCount", uploadedCount)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun handleDeleteFile(session: IHTTPSession): Response {
    val path = session.parameters["path"]?.firstOrNull() ?: ""
    val success = fileRepo.deleteFile(path)
    val resJson = JSONObject().apply {
      put("success", success)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }

  private fun serveClipboard(): Response {
    var clipText = ""
    val latch = CountDownLatch(1)
    mainHandler.post {
      try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
          clipText = clip.getItemAt(0).text?.toString() ?: ""
        }
      } catch (_: Exception) {
      } finally {
        latch.countDown()
      }
    }
    latch.await(500, TimeUnit.MILLISECONDS)

    val json = JSONObject().apply {
      put("text", clipText)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
  }

  private fun handleSetClipboard(session: IHTTPSession): Response {
    val map = HashMap<String, String>()
    session.parseBody(map)
    val postData = map["postData"] ?: ""
    val json = if (postData.isNotBlank()) JSONObject(postData) else JSONObject()
    val textToCopy = json.optString("text", "")

    mainHandler.post {
      try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AirPortal", textToCopy)
        cm?.setPrimaryClip(clip)
      } catch (_: Exception) {
      }
    }

    val resJson = JSONObject().apply {
      put("success", true)
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", resJson.toString())
  }
}
