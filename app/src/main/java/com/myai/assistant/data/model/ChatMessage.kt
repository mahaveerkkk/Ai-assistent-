// File: app/src/main/java/com/myai/assistant/data/model/ChatMessage.kt
// Chat Message — Room Entity + Data Model

package com.myai.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ek chat message — ya toh user ki ya AI ki
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val content: String,              // Message ka text
    val sender: MessageSender,        // USER ya AI
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT,

    // AI response ke extra fields
    val actionType: String? = null,   // CALL, SMS, OPEN_APP, etc.
    val actionData: String? = null,   // Action ke liye data (JSON)
    val isError: Boolean = false,     // Kya error message hai?
    val isLoading: Boolean = false    // Kya abhi loading hai? (AI soch raha hai)
)

/**
 * Message kisne bheja
 */
enum class MessageSender {
    USER,       // User ne bheja
    AI,         // AI ne jawab diya
    SYSTEM      // System notification (permission granted, service started, etc.)
}

/**
 * Message ka type
 */
enum class MessageType {
    TEXT,       // Normal text message
    VOICE,     // Voice se convert hua text
    IMAGE,     // Image attachment
    ACTION,    // AI ne koi action liya (call kiya, app khola, etc.)
    ERROR      // Error message
}
