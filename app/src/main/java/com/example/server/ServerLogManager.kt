package com.example.server

import com.example.data.model.ServerLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ServerLogManager {

  private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
  val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

  private val lock = Any()
  private const val MAX_LOG_SIZE = 150

  fun addLog(method: String, uri: String, statusCode: Int, clientIp: String, details: String = "") {
    synchronized(lock) {
      val entry = ServerLogEntry(
        id = UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        method = method,
        uri = uri,
        statusCode = statusCode,
        clientIp = clientIp,
        details = details
      )
      val updated = ArrayList<ServerLogEntry>(_logs.value.size + 1)
      updated.add(entry)
      updated.addAll(_logs.value.take(MAX_LOG_SIZE - 1))
      _logs.value = updated
    }
  }

  fun clearLogs() {
    synchronized(lock) {
      _logs.value = emptyList()
    }
  }
}
