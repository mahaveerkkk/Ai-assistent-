// File: app/src/main/java/com/myai/assistant/data/db/ChatDao.kt
// Chat DAO — Database operations for chat messages

package com.myai.assistant.data.db

import androidx.room.*
import com.myai.assistant.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    /**
     * Saare messages time ke hisaab se (latest last)
     * Flow use kiya hai — automatically update hoga jab data badle
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    /**
     * Latest N messages (for quick load)
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<ChatMessage>>

    /**
     * Ek message insert karo
     * Returns: inserted row ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    /**
     * Multiple messages insert karo
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    /**
     * Message update karo (e.g., loading → complete)
     */
    @Update
    suspend fun updateMessage(message: ChatMessage)

    /**
     * Ek message delete karo
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    /**
     * Saari chat history clear karo
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    /**
     * Total messages count
     */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int

    /**
     * Search messages by content
     */
    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessage>>

    /**
     * Delete messages older than the given timestamp
     */
    @Query("DELETE FROM chat_messages WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)
}
