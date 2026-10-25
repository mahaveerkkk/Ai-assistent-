// File: app/src/main/java/com/myai/assistant/features/contacts/ContactsHelper.kt
// Contacts Helper — Contacts padhna + Calls karna + WhatsApp number formatting

package com.myai.assistant.features.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
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
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        try {
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
                    contacts.add(Contact(name.trim(), number.trim(), photo))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts: ${e.message}", e)
        }
        return contacts.distinctBy { cleanPhoneNumber(it.phoneNumber) }
    }

    /**
     * Clean and normalize a phone number (keeps digits and leading +)
     */
    fun cleanPhoneNumber(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Format a phone number specifically for WhatsApp deep links (wa.me/NUMBER)
     * wa.me requires digits only, with country code (e.g., 919876543210)
     */
    fun formatForWhatsApp(phoneNumber: String, defaultCountryCode: String = "91"): String {
        var digits = phoneNumber.replace(Regex("[^0-9]"), "")

        // Handle double zero international prefix (0091 -> 91)
        if (digits.startsWith("00")) {
            digits = digits.substring(2)
        }

        // Handle single leading 0 (e.g., 09876543210 -> 919876543210 for 11-digit Indian numbers)
        if (digits.startsWith("0") && digits.length == 11) {
            digits = defaultCountryCode + digits.substring(1)
        }

        // Standard 10-digit mobile number without country code
        if (digits.length == 10) {
            digits = defaultCountryCode + digits
        }

        return digits
    }

    /**
     * Name se contact dhundo with fuzzy token matching and Hinglish postfix stripping
     */
    fun findContact(context: Context, name: String): Contact? {
        val allContacts = getAllContacts(context)
        if (allContacts.isEmpty()) return null

        // Clean common Hindi/Hinglish particles if included in name query
        val cleanedQuery = name.trim()
            .replace(Regex("(?i)\\b(ko|ki|ka|pe|par|ji|bhai|sir|sahab|beta)\\b"), "")
            .trim()
            .lowercase()

        if (cleanedQuery.isBlank()) return null

        // 1. Exact match (case-insensitive)
        allContacts.firstOrNull { it.name.trim().equals(cleanedQuery, ignoreCase = true) }?.let {
            return it
        }

        // 2. Starts with query
        allContacts.firstOrNull { it.name.trim().lowercase().startsWith(cleanedQuery) }?.let {
            return it
        }

        // 3. Token / word boundary match (e.g., "Rahul" matches "Rahul Sharma")
        val queryTokens = cleanedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        allContacts.firstOrNull { contact ->
            val contactTokens = contact.name.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            queryTokens.all { qToken -> contactTokens.any { cToken -> cToken.startsWith(qToken) || cToken == qToken } }
        }?.let {
            return it
        }

        // 4. Substring contains match
        allContacts.firstOrNull { it.name.contains(cleanedQuery, ignoreCase = true) }?.let {
            return it
        }

        // 5. Query contains contact name (e.g., query "call Rahul Sharma ji" matches "Rahul Sharma")
        return allContacts.firstOrNull { contact ->
            cleanedQuery.contains(contact.name.lowercase().trim())
        }
    }

    /**
     * Direct call karo, with fail-safe fallback to dialer if CALL_PHONE permission is missing or fails
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        if (cleanNumber.isBlank()) {
            Log.w(TAG, "Empty phone number provided to makeCall")
            return false
        }

        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "📞 Direct Calling: $cleanNumber")
                true
            } else {
                Log.w(TAG, "Permission CALL_PHONE not granted, opening dialer fallback")
                openDialer(context, cleanNumber)
                true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during ACTION_CALL, falling back to dialer: ${e.message}")
            openDialer(context, cleanNumber)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}, attempting dialer fallback", e)
            try {
                openDialer(context, cleanNumber)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Dialer fallback also failed: ${ex.message}")
                false
            }
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
            Log.w(TAG, "Contact not found: $name, opening dialer with query")
            openDialer(context, name)
            false
        }
    }

    /**
     * Dial pad kholo (call nahi lagega, sirf number dikhega)
     */
    fun openDialer(context: Context, phoneNumber: String) {
        try {
            val cleanNumber = cleanPhoneNumber(phoneNumber)
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📞 Opened dialer for $cleanNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer: ${e.message}")
        }
    }
}
