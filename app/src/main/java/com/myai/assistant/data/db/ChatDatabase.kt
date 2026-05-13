// File: app/src/main/java/com/myai/assistant/data/db/ChatDatabase.kt
// Room Database — Chat history store

package com.myai.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.myai.assistant.data.model.ChatMessage

@Database(
    entities = [ChatMessage::class],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
