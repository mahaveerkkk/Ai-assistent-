// File: app/src/main/java/com/myai/assistant/data/repository/ChatRepository.kt
// Chat Repository — Data layer abstraction

package com.myai.assistant.data.repository

import com.myai.assistant.data.db.ChatDao
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    /**
     * Saare messages ka Flow — UI automatically update hogi
     */
    fun getAllMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()

    /**
     * Recent messages (limited)
     */
    fun getRecentMessages(limit: Int = 50): Flow<List<ChatMessage>> =
        chatDao.getRecentMessages(limit)

    /**
     * User ka message save karo
     */
    suspend fun sendUserMessage(content: String, type: MessageType = MessageType.TEXT): Long {
        val message = ChatMessage(
            content = content,
            sender = MessageSender.USER,
            messageType = type
        )
        return chatDao.insertMessage(message)
    }

    /**
     * AI ka response save karo
     */
    suspend fun saveAiResponse(
        content: String,
        actionType: String? = null,
        actionData: String? = null,
        type: MessageType = MessageType.TEXT
    ): Long {
        val message = ChatMessage(
            content = content,
            sender = MessageSender.AI,
            messageType = type,
            actionType = actionType,
            actionData = actionData
        )
        return chatDao.insertMessage(message)
    }

    /**
     * System message save karo
     */
    suspend fun saveSystemMessage(content: String): Long {
        val message = ChatMessage(
            content = content,
            sender = MessageSender.SYSTEM,
            messageType = MessageType.TEXT
        )
        return chatDao.insertMessage(message)
    }

    /**
     * Loading placeholder insert karo (AI soch raha hai)
     */
    suspend fun insertLoadingMessage(): Long {
        val message = ChatMessage(
            content = "Soch raha hoon...",
            sender = MessageSender.AI,
            isLoading = true
        )
        return chatDao.insertMessage(message)
    }

    /**
     * Loading message ko actual response se replace karo
     */
    suspend fun replaceLoadingWithResponse(
        loadingId: Long,
        content: String,
        actionType: String? = null,
        actionData: String? = null
    ) {
        val updated = ChatMessage(
            id = loadingId,
            content = content,
            sender = MessageSender.AI,
            isLoading = false,
            actionType = actionType,
            actionData = actionData
        )
        chatDao.updateMessage(updated)
    }

    /**
     * Error message save karo
     */
    suspend fun saveErrorMessage(error: String): Long {
        val message = ChatMessage(
            content = error,
            sender = MessageSender.AI,
            messageType = MessageType.ERROR,
            isError = true
        )
        return chatDao.insertMessage(message)
    }

    /**
     * Ek message delete karo
     */
    suspend fun deleteMessage(message: ChatMessage) = chatDao.deleteMessage(message)

    /**
     * Saari history clear karo
     */
    suspend fun clearAll() = chatDao.clearAllMessages()

    /**
     * Messages search karo
     */
    fun searchMessages(query: String): Flow<List<ChatMessage>> = chatDao.searchMessages(query)
}
