// File: app/src/main/java/com/myai/assistant/service/BootReceiver.kt
// Boot Receiver — Phone restart hone pe AI assistant auto-start

package com.myai.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot Receiver — Phone boot hone pe assistant service auto-start karo
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            Log.d(TAG, "📱 Boot completed — starting AI Assistant")

            // Check if user has enabled auto-start
            val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_on_boot", true)

            if (autoStart) {
                AssistantForegroundService.start(context)
                Log.d(TAG, "✅ Assistant service auto-started")
            }
        }
    }
}
