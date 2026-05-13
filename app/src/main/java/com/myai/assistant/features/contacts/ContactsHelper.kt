// File: app/src/main/java/com/myai/assistant/features/contacts/ContactsHelper.kt
// Contacts Helper — Contacts padhna + Calls karna

package com.myai.assistant.features.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

data class Contact(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

@Singleton
class ContactsHelper @Inject constructor() {

    companion object {
        private const val TAG = "ContactsHelper"
    }

    /**
     * Sabhi contacts ki list lo
     */
    fun getAllContacts(context: Context): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return emptyList()

        val contacts = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                val photo = it.getString(2)
                contacts.add(Contact(name, number, photo))
            }
        }
        return contacts.distinctBy { it.phoneNumber }
    }

    /**
     * Name se contact dhundo
     */
    fun findContact(context: Context, name: String): Contact? {
        return getAllContacts(context).find {
            it.name.contains(name, ignoreCase = true)
        }
    }

    /**
     * Direct call karo
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📞 Calling: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}")
            false
        }
    }

    /**
     * Name se call karo (contact dhund ke)
     */
    fun callByName(context: Context, name: String): Boolean {
        val contact = findContact(context, name)
        return if (contact != null) {
            makeCall(context, contact.phoneNumber)
        } else {
            Log.w(TAG, "Contact not found: $name")
            false
        }
    }

    /**
     * Dial pad kholo (call nahi lagega, sirf number dikhega)
     */
    fun openDialer(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
