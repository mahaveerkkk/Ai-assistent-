// File: app/src/main/java/com/myai/assistant/ui/components/MessageBubble.kt
// Chat Message Bubble — Beautiful gradient bubbles

package com.myai.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import com.myai.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM
    val isError = message.isError
    val isAction = message.messageType == MessageType.ACTION

    // System messages — center aligned, subtle
    if (isSystem) {
        SystemMessageBubble(message)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // AI Avatar (left side, sirf AI messages ke liye)
        if (!isUser) {
            AiAvatar(modifier = Modifier.padding(end = 8.dp, top = 4.dp))
        }

        // Message bubble
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (isUser) 20.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 20.dp
                        )
                    )
                    .then(
                        when {
                            isError -> Modifier.background(ErrorColor.copy(alpha = 0.15f))
                            isUser -> Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        UserBubble,
                                        UserBubble.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            isAction -> Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        TertiaryContainer,
                                        TertiaryContainer.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            else -> Modifier.background(AiBubble)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Action badge (agar action message hai)
                    if (isAction && message.actionType != null) {
                        ActionBadge(actionType = message.actionType)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Voice badge (agar voice se aaya)
                    if (message.messageType == MessageType.VOICE && isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Voice",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Message text
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isError -> ErrorColor
                            isUser -> Color.White
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        lineHeight = 20.sp
                    )

                    // Error icon
                    if (isError) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErrorColor
                            )
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(
                    top = 4.dp,
                    start = if (!isUser) 4.dp else 0.dp,
                    end = if (isUser) 4.dp else 0.dp
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// AI AVATAR — Small circle with AI icon
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AiAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(GradientStart, GradientMiddle)
                ),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "AI",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// ACTION BADGE — Action type indicator
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ActionBadge(actionType: String) {
    val (icon, label) = when (actionType.uppercase()) {
        "CALL" -> Icons.Filled.Call to "Calling..."
        "SMS" -> Icons.Filled.Sms to "Sending SMS..."
        "OPEN_APP" -> Icons.Filled.OpenInNew to "Opening App..."
        "CAMERA" -> Icons.Filled.CameraAlt to "Camera..."
        "SEARCH" -> Icons.Filled.Search to "Searching..."
        else -> Icons.Filled.SmartToy to actionType
    }

    Row(
        modifier = Modifier
            .background(
                color = TertiaryDark.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TertiaryDark,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TertiaryDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// SYSTEM MESSAGE — Center aligned, subtle
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SystemMessageBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// TIME FORMATTER
// ═══════════════════════════════════════════════════════════════
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
