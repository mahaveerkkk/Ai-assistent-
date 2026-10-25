// File: app/src/main/java/com/myai/assistant/features/messages/SmsHelper.kt
// SMS Helper — SMS bhejna + padhna + fail-safe SMS compose fallback

package com.myai.assistant.features.messages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

data class SmsMessage(
    val address: String,    // Phone number
    val body: String,       // Message text
    val date: Long,         // Timestamp
    val type: Int           // 1=received, 2=sent
)

@Singleton
class SmsHelper @Inject constructor() {

    companion object {
        private const val TAG = "SmsHelper"
    }

    /**
     * SMS bhejo. If SEND_SMS permission is not granted or fails, fall back to opening SMS app
     */
    fun sendSms(context: Context, phoneNumber: String, message: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (cleanNumber.isBlank()) {
            Log.e(TAG, "Empty phone number provided for SMS")
            return false
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted, opening SMS app fallback")
            return openSmsApp(context, cleanNumber, message)
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Log.w(TAG, "SmsManager unavailable, opening SMS app fallback")
                return openSmsApp(context, cleanNumber, message)
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            Log.d(TAG, "💬 Direct SMS sent to $cleanNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Direct SMS failed: ${e.message}, opening SMS app fallback", e)
            openSmsApp(context, cleanNumber, message)
        }
    }

    /**
     * Fallback to opening default SMS composer with pre-filled number and message
     */
    fun openSmsApp(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
            val uri = Uri.parse("smsto:$cleanNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "💬 Opened SMS app for $cleanNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS app: ${e.message}", e)
            false
        }
    }

    /**
     * Recent SMS padho
     */
    fun getRecentSms(context: Context, limit: Int = 20): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_SMS permission not granted")
            return emptyList()
        }

        val messages = mutableListOf<SmsMessage>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    messages.add(SmsMessage(
                        address = it.getString(0) ?: "",
                        body = it.getString(1) ?: "",
                        date = it.getLong(2),
                        type = it.getInt(3)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS: ${e.message}", e)
        }
        return messages
    }

    /**
     * Kisi specific number ke messages padho
     */
    fun getMessagesFrom(context: Context, phoneNumber: String, limit: Int = 10): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_SMS permission not granted")
            return emptyList()
        }

        val messages = mutableListOf<SmsMessage>()
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            val lastDigits = if (cleanNumber.length >= 10) cleanNumber.takeLast(10) else cleanNumber
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.ADDRESS} LIKE ?",
                arrayOf("%$lastDigits%"),
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    messages.add(SmsMessage(
                        address = it.getString(0) ?: "",
                        body = it.getString(1) ?: "",
                        date = it.getLong(2),
                        type = it.getInt(3)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading messages from $phoneNumber: ${e.message}", e)
        }
        return messages
    }
}
