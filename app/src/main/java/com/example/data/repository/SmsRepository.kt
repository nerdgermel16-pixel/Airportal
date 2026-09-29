package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.example.data.model.SmsMessageItem
import com.example.data.model.SmsThreadItem

class SmsRepository(
  private val context: Context,
  private val contactsRepository: ContactsRepository
) {

  private val smsUri: Uri = Telephony.Sms.CONTENT_URI

  private val prefs by lazy {
    context.getSharedPreferences("airportal_sms_prefs", Context.MODE_PRIVATE)
  }

  private fun getDeletedThreadIds(): Set<Long> {
    val set = prefs.getStringSet("deleted_thread_ids", emptySet()) ?: emptySet()
    return set.mapNotNull { it.toLongOrNull() }.toSet()
  }

  private fun markThreadsDeleted(ids: Collection<Long>) {
    if (ids.isEmpty()) return
    val current = getDeletedThreadIds().toMutableSet()
    current.addAll(ids)
    prefs.edit().putStringSet("deleted_thread_ids", current.map { it.toString() }.toSet()).apply()
  }

  private fun unmarkDeletedThreadForAddress(address: String) {
    val currentDeleted = getDeletedThreadIds().toMutableSet()
    if (currentDeleted.isEmpty()) return

    val rawMessages = getAllRawMessages(limit = 1000)
    val cleanTarget = address.replace(Regex("[^0-9+]"), "")
    if (cleanTarget.isBlank()) return

    val matchingThreadIds = rawMessages.filter {
      val cleanAddr = it.address.replace(Regex("[^0-9+]"), "")
      cleanAddr.isNotEmpty() && (cleanAddr == cleanTarget || cleanAddr.endsWith(cleanTarget) || cleanTarget.endsWith(cleanAddr))
    }.map { it.threadId }.toSet()

    if (matchingThreadIds.isNotEmpty()) {
      currentDeleted.removeAll(matchingThreadIds)
      prefs.edit().putStringSet("deleted_thread_ids", currentDeleted.map { it.toString() }.toSet()).apply()
    }
  }

  fun getAllRawMessages(limit: Int = 400): List<SmsMessageItem> {
    val messages = mutableListOf<SmsMessageItem>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
      Telephony.Sms._ID,
      Telephony.Sms.THREAD_ID,
      Telephony.Sms.ADDRESS,
      Telephony.Sms.BODY,
      Telephony.Sms.DATE,
      Telephony.Sms.TYPE,
      Telephony.Sms.READ
    )

    try {
      val cursor = contentResolver.query(
        smsUri,
        projection,
        null,
        null,
        "${Telephony.Sms.DATE} DESC LIMIT $limit"
      )

      cursor?.use {
        val idIdx = it.getColumnIndex(Telephony.Sms._ID)
        val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
        val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
        val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
        val readIdx = it.getColumnIndex(Telephony.Sms.READ)

        val contactCache = mutableMapOf<String, String?>()

        while (it.moveToNext()) {
          val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
          val threadId = if (threadIdx >= 0) it.getLong(threadIdx) else 0L
          val address = (if (addressIdx >= 0) it.getString(addressIdx) else null) ?: "Unknown"
          val body = (if (bodyIdx >= 0) it.getString(bodyIdx) else null) ?: ""
          val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
          val type = if (typeIdx >= 0) it.getInt(typeIdx) else 1
          val read = if (readIdx >= 0) it.getInt(readIdx) else 1

          val contactName = contactCache.getOrPut(address) {
            contactsRepository.getContactNameForNumber(address)
          }

          messages.add(
            SmsMessageItem(
              id = id,
              threadId = threadId,
              address = address,
              contactName = contactName,
              body = body,
              date = date,
              type = type,
              read = read
            )
          )
        }
      }
    } catch (_: SecurityException) {
      // Permission not granted
    } catch (_: Exception) {
    }

    return messages
  }

  fun getAllMessages(limit: Int = 400): List<SmsMessageItem> {
    val deletedIds = getDeletedThreadIds()
    val all = getAllRawMessages(limit)
    if (deletedIds.isEmpty()) return all
    return all.filter { !deletedIds.contains(it.threadId) }
  }

  fun getThreads(): List<SmsThreadItem> {
    val messages = getAllMessages(limit = 800)
    val grouped = messages.groupBy { it.threadId }

    return grouped.map { (threadId, threadMessages) ->
      val latest = threadMessages.maxByOrNull { it.date } ?: threadMessages.first()
      val unreadCount = threadMessages.count { it.type == 1 && it.read == 0 }
      SmsThreadItem(
        threadId = threadId,
        address = latest.address,
        contactName = latest.contactName,
        snippet = latest.body,
        date = latest.date,
        messageCount = threadMessages.size,
        unreadCount = unreadCount
      )
    }.sortedByDescending { it.date }
  }

  fun markThreadAsRead(threadId: Long) {
    try {
      val values = ContentValues().apply {
        put(Telephony.Sms.READ, 1)
      }
      context.contentResolver.update(
        smsUri,
        values,
        "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
        arrayOf(threadId.toString())
      )
    } catch (_: Exception) {
    }
  }

  fun getMessagesForThread(threadId: Long): List<SmsMessageItem> {
    if (getDeletedThreadIds().contains(threadId)) {
      return emptyList()
    }
    markThreadAsRead(threadId)
    val messages = mutableListOf<SmsMessageItem>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
      Telephony.Sms._ID,
      Telephony.Sms.THREAD_ID,
      Telephony.Sms.ADDRESS,
      Telephony.Sms.BODY,
      Telephony.Sms.DATE,
      Telephony.Sms.TYPE,
      Telephony.Sms.READ
    )

    try {
      val cursor = contentResolver.query(
        smsUri,
        projection,
        "${Telephony.Sms.THREAD_ID} = ?",
        arrayOf(threadId.toString()),
        "${Telephony.Sms.DATE} ASC"
      )

      cursor?.use {
        val idIdx = it.getColumnIndex(Telephony.Sms._ID)
        val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
        val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
        val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
        val readIdx = it.getColumnIndex(Telephony.Sms.READ)

        val contactCache = mutableMapOf<String, String?>()

        while (it.moveToNext()) {
          val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
          val thId = if (threadIdx >= 0) it.getLong(threadIdx) else threadId
          val address = (if (addressIdx >= 0) it.getString(addressIdx) else null) ?: "Unknown"
          val body = (if (bodyIdx >= 0) it.getString(bodyIdx) else null) ?: ""
          val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
          val type = if (typeIdx >= 0) it.getInt(typeIdx) else 1
          val read = if (readIdx >= 0) it.getInt(readIdx) else 1

          val contactName = contactCache.getOrPut(address) {
            contactsRepository.getContactNameForNumber(address)
          }

          messages.add(
            SmsMessageItem(
              id = id,
              threadId = thId,
              address = address,
              contactName = contactName,
              body = body,
              date = date,
              type = type,
              read = read
            )
          )
        }
      }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }

    return messages
  }

  fun sendSms(destinationAddress: String, text: String): Result<Boolean> {
    if (destinationAddress.isBlank()) {
      return Result.failure(IllegalArgumentException("Recipient phone number cannot be empty"))
    }
    if (text.isBlank()) {
      return Result.failure(IllegalArgumentException("Message text cannot be empty"))
    }

    return try {
      val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
      } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
      }

      val divided = smsManager.divideMessage(text)
      if (divided.size > 1) {
        smsManager.sendMultipartTextMessage(destinationAddress, null, divided, null, null)
      } else {
        smsManager.sendTextMessage(destinationAddress, null, text, null, null)
      }

      // Record in local sent SMS provider so it appears in threads immediately
      try {
        val values = ContentValues().apply {
          put(Telephony.Sms.ADDRESS, destinationAddress)
          put(Telephony.Sms.BODY, text)
          put(Telephony.Sms.DATE, System.currentTimeMillis())
          put(Telephony.Sms.READ, 1)
          put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
      } catch (_: Exception) {
      }

      // Unhide thread if user is sending a new message to this recipient
      unmarkDeletedThreadForAddress(destinationAddress)

      Result.success(true)
    } catch (se: SecurityException) {
      Result.failure(SecurityException("SMS permission not granted. Please allow SEND_SMS in app settings.", se))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun deleteThread(threadId: Long): Boolean {
    markThreadsDeleted(listOf(threadId))
    try {
      context.contentResolver.delete(
        smsUri,
        "${Telephony.Sms.THREAD_ID} = ?",
        arrayOf(threadId.toString())
      )
    } catch (_: Exception) {
      try {
        val conversationUri = Uri.parse("content://sms/conversations/$threadId")
        context.contentResolver.delete(conversationUri, null, null)
      } catch (_: Exception) {
      }
    }
    return true
  }

  fun deleteThreads(threadIds: List<Long>): Int {
    if (threadIds.isEmpty()) return 0
    markThreadsDeleted(threadIds)
    for (id in threadIds) {
      try {
        context.contentResolver.delete(
          smsUri,
          "${Telephony.Sms.THREAD_ID} = ?",
          arrayOf(id.toString())
        )
      } catch (_: Exception) {
      }
    }
    return threadIds.size
  }
}
