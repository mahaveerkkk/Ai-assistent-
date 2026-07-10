// File: app/src/main/java/com/myai/assistant/ui/widget/MyAIWidgetProvider.kt
package com.myai.assistant.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.myai.assistant.R
import com.myai.assistant.ui.MainActivity

class MyAIWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Chat button click intent
            val chatIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val chatPendingIntent = PendingIntent.getActivity(
                context,
                0,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_chat, chatPendingIntent)

            // Mic button click intent
            val micIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("launch_voice", true)
            }
            val micPendingIntent = PendingIntent.getActivity(
                context,
                1, // unique request code to distinguish from chat intent
                micIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_mic, micPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
