// File: app/src/main/java/com/myai/assistant/di/AppModule.kt
// Hilt DI Module — Sabhi dependencies provide karo

package com.myai.assistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.myai.assistant.data.db.ChatDao
import com.myai.assistant.data.db.ChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "myai_settings_prefs",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "myai_settings_prefs"))
    }
)

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

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }
}
