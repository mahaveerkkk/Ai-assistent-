// File: app/src/main/java/com/myai/assistant/features/device/AlarmHelper.kt
// Alarm & Timer Helper — Alarm set, Timer set via Android intents

package com.myai.assistant.features.device

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlarmHelper"
    }

    /**
     * ⏰ Alarm set karo
     * @param hour 0-23 format
     * @param minute 0-59
     * @param label Alarm label (optional)
     */
    fun setAlarm(hour: Int, minute: Int, label: String = "MyAI Alarm"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // UI dikhao confirm ke liye
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "⏰ Alarm set: $hour:$minute ($label)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Set alarm failed: ${e.message}")
            false
        }
    }

    /**
     * ⏱️ Timer set karo
     * @param seconds Timer duration in seconds
     * @param label Timer label (optional)
     */
    fun setTimer(seconds: Int, label: String = "MyAI Timer"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "⏱️ Timer set: ${seconds}s ($label)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Set timer failed: ${e.message}")
            false
        }
    }

    /**
     * ⏰ Sabhi alarms dikhao
     */
    fun showAlarms(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Show alarms failed: ${e.message}")
            false
        }
    }
}
