// File: app/src/main/java/com/myai/assistant/features/device/CalendarHelper.kt
// Calendar Helper — Events create karo, upcoming events padhho

package com.myai.assistant.features.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String = "",
    val description: String = ""
)

@Singleton
class CalendarHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CalendarHelper"
    }

    /**
     * 📅 Naya event create karo (Calendar app open hoga)
     */
    fun createEvent(
        title: String,
        beginTimeMillis: Long = System.currentTimeMillis() + 3600000, // default: 1 hour from now
        endTimeMillis: Long = beginTimeMillis + 3600000, // default: 1 hour duration
        location: String = "",
        description: String = ""
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📅 Creating event: $title")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Create event failed: ${e.message}")
            false
        }
    }

    /**
     * 📋 Upcoming events padhho (next 24 hours)
     */
    fun getUpcomingEvents(hoursAhead: Int = 24): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALENDAR permission not granted")
            return emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val end = now + (hoursAhead * 3600000L)

        try {
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(now.toString(), end.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    events.add(CalendarEvent(
                        title = it.getString(0) ?: "",
                        startTime = it.getLong(1),
                        endTime = it.getLong(2),
                        location = it.getString(3) ?: "",
                        description = it.getString(4) ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read calendar failed: ${e.message}")
        }

        return events
    }

    /**
     * 📅 Calendar app kholo
     */
    fun openCalendar(): Boolean {
        return try {
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW, builder.build()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Open calendar failed: ${e.message}")
            false
        }
    }
}
