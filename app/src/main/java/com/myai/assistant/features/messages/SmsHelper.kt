// File: app/src/main/java/com/myai/assistant/features/messages/SmsHelper.kt
// SMS Helper — SMS bhejnaa + padhna

package com.myai.assistant.features.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
     * SMS bhejo
     */
    fun sendSms(context: Context, phoneNumber: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return false
        }
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            // Lambe messages ke liye multi-part
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d(TAG, "💬 SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
            false
        }
    }

    /**
     * Recent SMS padhho
     */
    fun getRecentSms(context: Context, limit: Int = 20): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return emptyList()

        val messages = mutableListOf<SmsMessage>()
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
        return messages
    }

    /**
     * Kisi specific number ke messages padhho
     */
    fun getMessagesFrom(context: Context, phoneNumber: String, limit: Int = 10): List<SmsMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return emptyList()

        val messages = mutableListOf<SmsMessage>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms.ADDRESS} LIKE ?",
            arrayOf("%${phoneNumber.takeLast(10)}%"),
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
        return messages
    }
}
