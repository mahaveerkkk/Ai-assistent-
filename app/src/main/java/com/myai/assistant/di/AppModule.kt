// File: app/src/main/java/com/myai/assistant/di/AppModule.kt
// Hilt DI Module — Sabhi dependencies provide karo

package com.myai.assistant.di

import android.content.Context
import androidx.room.Room
import com.myai.assistant.data.db.ChatDao
import com.myai.assistant.data.db.ChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ═══════════════════════════════════════
    // ROOM DATABASE
    // ═══════════════════════════════════════
    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context
    ): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "myai_chat_db"
        )
            .fallbackToDestructiveMigration()  // Development mein — production mein migration likho
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: ChatDatabase): ChatDao {
        return database.chatDao()
    }
}
