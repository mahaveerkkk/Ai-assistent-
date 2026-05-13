// File: app/src/main/java/com/myai/assistant/features/messages/SmsReceiver.kt
// SMS Receiver — Incoming SMS listen karo

package com.myai.assistant.features.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * SMS Receiver — Jab nayi SMS aaye tab trigger hota hai
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        var onSmsReceived: ((from: String, body: String) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val from = sms.displayOriginatingAddress ?: "Unknown"
            val body = sms.messageBody ?: ""
            Log.d(TAG, "📩 SMS from $from: ${body.take(50)}")
            onSmsReceived?.invoke(from, body)
        }
    }
}
