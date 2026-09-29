package com.example.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.data.model.ContactItem

class ContactsRepository(private val context: Context) {

  fun getContacts(searchQuery: String? = null): List<ContactItem> {
    val contactsMap = mutableMapOf<String, MutableContact>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
    )

    val selection = if (!searchQuery.isNullOrBlank()) {
      "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
    } else null

    val selectionArgs = if (!searchQuery.isNullOrBlank()) {
      arrayOf("%$searchQuery%", "%$searchQuery%")
    } else null

    try {
      val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
      )

      cursor?.use {
        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

        while (it.moveToNext()) {
          val contactId = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""
          val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Unknown" else "Unknown"
          val rawNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
          val photoUri = if (photoIndex >= 0) it.getString(photoIndex) else null

          val cleanedNumber = rawNumber.replace("\\s+".toRegex(), "")
          if (cleanedNumber.isNotEmpty()) {
            val key = if (contactId.isNotEmpty()) contactId else name
            val existing = contactsMap.getOrPut(key) {
              MutableContact(
                id = contactId,
                displayName = name,
                primaryNumber = cleanedNumber,
                photoUri = photoUri
              )
            }
            if (!existing.numbers.contains(cleanedNumber)) {
              existing.numbers.add(cleanedNumber)
            }
          }
        }
      }
    } catch (_: SecurityException) {
      // Permission not granted
    } catch (_: Exception) {
    }

    return contactsMap.values.map {
      ContactItem(
        id = it.id,
        displayName = it.displayName,
        primaryNumber = it.primaryNumber,
        numbers = it.numbers.toList(),
        photoUri = it.photoUri
      )
    }.sortedBy { it.displayName.lowercase() }
  }

  fun getContactNameForNumber(number: String): String? {
    if (number.isBlank()) return null
    try {
      val uri = android.net.Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        android.net.Uri.encode(number)
      )
      val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
          if (nameIndex >= 0) {
            return cursor.getString(nameIndex)
          }
        }
      }
    } catch (_: Exception) {
    }
    return null
  }

  private data class MutableContact(
    val id: String,
    val displayName: String,
    var primaryNumber: String,
    val numbers: MutableList<String> = mutableListOf(),
    val photoUri: String? = null
  )
}
