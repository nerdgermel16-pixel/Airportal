package com.example.data.repository
 
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import com.example.data.model.CallLogItem
 
class CallLogRepository(
  private val context: Context,
  private val contactsRepository: ContactsRepository? = null
) {
 
  fun getCallLogs(limit: Int = 100): List<CallLogItem> {
    val list = mutableListOf<CallLogItem>()
    val contentResolver = context.contentResolver
 
    val projection = arrayOf(
      CallLog.Calls._ID,
      CallLog.Calls.NUMBER,
      CallLog.Calls.CACHED_NAME,
      CallLog.Calls.TYPE,
      CallLog.Calls.DATE,
      CallLog.Calls.DURATION
    )
 
    try {
      val cursor = contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        "${CallLog.Calls.DATE} DESC"
      )
 
      cursor?.use {
        val idIdx = it.getColumnIndex(CallLog.Calls._ID)
        val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
        val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
        val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
        val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
 
        while (it.moveToNext() && list.size < limit) {
          val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
          val number = (if (numIdx >= 0) it.getString(numIdx) else null) ?: "Unknown"
          var name = if (nameIdx >= 0) it.getString(nameIdx) else null

          if (name.isNullOrBlank() && contactsRepository != null) {
            name = contactsRepository.getContactNameForNumber(number)
          }

          val type = if (typeIdx >= 0) it.getInt(typeIdx) else 1
          val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
          val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L
 
          list.add(
            CallLogItem(
              id = id,
              number = number,
              name = name,
              type = type,
              date = date,
              duration = duration
            )
          )
        }
      }
    } catch (_: SecurityException) {
      // Permission not granted
    } catch (_: Exception) {
    }
 
    return list
  }
}
