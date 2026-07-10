// File: app/src/main/java/com/myai/assistant/ui/components/MessageBubble.kt
// Chat Message Bubble — Beautiful gradient bubbles + Markdown + Visual Cards + Themes

package com.myai.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import com.myai.assistant.data.repository.SettingsRepository
import com.myai.assistant.ui.theme.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

// ═══════════════════════════════════════════════════════
// CHAT THEMES DEFINITION
// ═══════════════════════════════════════════════════════
sealed class ChatBubbleTheme {
    abstract val userBubbleBrush: Brush
    abstract val userTextColor: Color
    abstract val aiBubbleBrush: Brush
    abstract val aiTextColor: Color
    abstract val hasBorder: Boolean
    abstract val borderColor: Color

    object ModernBlueGradient : ChatBubbleTheme() {
        override val userBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF3B82F6),
                Color(0xFF1D4ED8)
            )
        )
        override val userTextColor = Color.White
        override val aiBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF1F2937),
                Color(0xFF111827)
            )
        )
        override val aiTextColor = Color.White
        override val hasBorder = false
        override val borderColor = Color.Transparent
    }

    object CyberpunkPurple : ChatBubbleTheme() {
        override val userBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF8B5CF6), // Neon Purple
                Color(0xFFEC4899)  // Magenta
            )
        )
        override val userTextColor = Color.White
        override val aiBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF180A2B), // Very dark purple
                Color(0xFF0F051D)
            )
        )
        override val aiTextColor = Color(0xFFF472B6) // Magenta tinted text
        override val hasBorder = true
        override val borderColor = Color(0xFF8B5CF6).copy(alpha = 0.5f)
    }

    object MinimalistDark : ChatBubbleTheme() {
        override val userBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF374151), // Charcoal/Slate
                Color(0xFF1F2937)
            )
        )
        override val userTextColor = Color.White
        override val aiBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF111827), // Deep gray
                Color(0xFF111827)
            )
        )
        override val aiTextColor = Color(0xFFE5E7EB)
        override val hasBorder = true
        override val borderColor = Color(0xFF4B5563).copy(alpha = 0.4f)
    }

    object ForestGreen : ChatBubbleTheme() {
        override val userBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF059669), // Emerald
                Color(0xFF10B981)  // Lime green / Mint
            )
        )
        override val userTextColor = Color.White
        override val aiBubbleBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF064E3B), // Deep Forest Green
                Color(0xFF022C22)
            )
        )
        override val aiTextColor = Color(0xFFD1FAE5) // Soft green text
        override val hasBorder = false
        override val borderColor = Color.Transparent
    }

    companion object {
        fun fromString(theme: String): ChatBubbleTheme {
            return when (theme) {
                "Cyberpunk Purple" -> CyberpunkPurple
                "Minimalist Dark" -> MinimalistDark
                "Forest Green" -> ForestGreen
                else -> ModernBlueGradient
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// DATA CLASSES FOR CARDS
// ═══════════════════════════════════════════════════════
data class DeviceInfoData(
    val batteryPercentage: Int,
    val storageUsed: Float,
    val storageTotal: Float,
    val ramUsed: Float,
    val ramTotal: Float
)

data class LocationInfoData(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class AlarmTimerInfoData(
    val hour: Int,
    val minute: Int,
    val seconds: Int,
    val isAlarm: Boolean,
    val label: String
)

data class ContactSmsInfoData(
    val type: String,
    val nameOrPhone: String,
    val smsBody: String
)

// ═══════════════════════════════════════════════════════
// MARKDOWN SEGMENT MODELS
// ═══════════════════════════════════════════════════════
sealed interface MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

// ═══════════════════════════════════════════════════════
// PARSERS HELPERS
// ═══════════════════════════════════════════════════════
fun splitMarkdownSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = text.split("\n")
    var inCodeBlock = false
    var currentLanguage = ""
    val currentCode = StringBuilder()
    val currentText = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                segments.add(MarkdownSegment.CodeBlock(currentLanguage, currentCode.toString().trimEnd()))
                currentCode.setLength(0)
                inCodeBlock = false
            } else {
                if (currentText.isNotEmpty()) {
                    segments.add(MarkdownSegment.Text(currentText.toString().trimEnd('\n')))
                    currentText.setLength(0)
                }
                currentLanguage = line.trim().substring(3).trim()
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                currentCode.append(line).append("\n")
            } else {
                currentText.append(line).append("\n")
            }
        }
    }

    if (inCodeBlock) {
        segments.add(MarkdownSegment.CodeBlock(currentLanguage, currentCode.toString().trimEnd()))
    } else if (currentText.isNotEmpty()) {
        segments.add(MarkdownSegment.Text(currentText.toString().trimEnd('\n')))
    }

    return segments.filter {
        when (it) {
            is MarkdownSegment.Text -> it.content.isNotBlank()
            is MarkdownSegment.CodeBlock -> it.code.isNotBlank()
        }
    }
}

fun parseInlineMarkdown(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val nextBold = text.indexOf("**", index)
            val nextItalic = text.indexOf("*", index)
            val nextCode = text.indexOf("`", index)

            val minIndex = listOf(
                if (nextBold != -1) nextBold else Int.MAX_VALUE,
                if (nextItalic != -1) nextItalic else Int.MAX_VALUE,
                if (nextCode != -1) nextCode else Int.MAX_VALUE
            ).minOrNull() ?: Int.MAX_VALUE

            if (minIndex == Int.MAX_VALUE) {
                append(text.substring(index))
                break
            }

            if (minIndex > index) {
                append(text.substring(index, minIndex))
            }

            if (minIndex == nextBold) {
                val endBold = text.indexOf("**", nextBold + 2)
                if (endBold != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(nextBold + 2, endBold))
                    pop()
                    index = endBold + 2
                } else {
                    append("**")
                    index = nextBold + 2
                }
            } else if (minIndex == nextItalic) {
                val endItalic = text.indexOf("*", nextItalic + 1)
                if (endItalic != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(nextItalic + 1, endItalic))
                    pop()
                    index = endItalic + 1
                } else {
                    append("*")
                    index = nextItalic + 1
                }
            } else {
                val endCode = text.indexOf("`", nextCode + 1)
                if (endCode != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = textColor.copy(alpha = 0.12f),
                            color = if (textColor == Color.White) Color(0xFFFCA5A5) else Color(0xFFEF4444)
                        )
                    )
                    append(text.substring(nextCode + 1, endCode))
                    pop()
                    index = endCode + 1
                } else {
                    append("`")
                    index = nextCode + 1
                }
            }
        }
    }
}

fun parseMarkdownText(text: String, textColor: Color): AnnotatedString {
    val lines = text.split("\n")
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            val isBullet = line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")
            val content = when {
                isBullet -> {
                    val indent = line.substringBefore("- ").substringBefore("* ")
                    val after = if (line.trimStart().startsWith("- ")) line.trimStart().removePrefix("- ") else line.trimStart().removePrefix("* ")
                    "$indent• $after"
                }
                else -> line
            }
            append(parseInlineMarkdown(content, textColor))
            if (i < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

fun parseDeviceInfo(text: String): DeviceInfoData? {
    val batteryRegex = Regex("Battery:\\s*(\\d+)")
    val storageRegex = Regex("Storage:\\s*([\\d.]+)\\s*GB\\s*/\\s*([\\d.]+)\\s*GB")
    val ramRegex = Regex("RAM:\\s*([\\d.]+)\\s*GB\\s*/\\s*([\\d.]+)\\s*GB")

    val batteryMatch = batteryRegex.find(text)
    val storageMatch = storageRegex.find(text)
    val ramMatch = ramRegex.find(text)

    if (batteryMatch == null && storageMatch == null && ramMatch == null) {
        return null
    }

    val batteryPct = batteryMatch?.groupValues?.get(1)?.toIntOrNull() ?: 80
    val storageUsed = storageMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 45f
    val storageTotal = storageMatch?.groupValues?.get(2)?.toFloatOrNull() ?: 128f
    val ramUsed = ramMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 4.2f
    val ramTotal = ramMatch?.groupValues?.get(2)?.toFloatOrNull() ?: 8f

    return DeviceInfoData(batteryPct, storageUsed, storageTotal, ramUsed, ramTotal)
}

fun parseLocationInfo(text: String): LocationInfoData? {
    val coordsRegex = Regex("Coordinates:\\s*([\\d.-]+)\\s*,\\s*([\\d.-]+)")
    val coordsMatch = coordsRegex.find(text)
    val address = text.substringAfter("📍 Aapki location:\n").substringBefore("\n\n").trim()

    val latitude = coordsMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 28.6139
    val longitude = coordsMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 77.2090

    if (coordsMatch == null && !text.contains("location", ignoreCase = true)) {
        return null
    }

    return LocationInfoData(
        address = if (address.isNotBlank() && !address.startsWith("Coordinates") && !address.contains("Aapki location")) address else "Connaught Place, New Delhi",
        latitude = latitude,
        longitude = longitude
    )
}

fun parseAlarmTimerInfo(content: String, actionType: String?, actionData: String?): AlarmTimerInfoData {
    var hour = 7
    var minute = 0
    var seconds = 60
    val isAlarm = actionType == "SET_ALARM" || content.contains("alarm", ignoreCase = true)
    var label = "MyAI Assistant"

    if (actionData != null && actionData.startsWith("{")) {
        try {
            val map = com.google.gson.Gson().fromJson(actionData, Map::class.java)
            if (isAlarm) {
                hour = (map["hour"]?.toString()?.toDoubleOrNull() ?: 7.0).toInt()
                minute = (map["minute"]?.toString()?.toDoubleOrNull() ?: 0.0).toInt()
                label = map["label"]?.toString() ?: "Alarm"
            } else {
                seconds = (map["seconds"]?.toString()?.toDoubleOrNull() ?: 60.0).toInt()
                label = map["label"]?.toString() ?: "Timer"
            }
        } catch (e: Exception) {}
    } else {
        val alarmMatch = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", RegexOption.IGNORE_CASE).find(content)
        if (alarmMatch != null) {
            hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = alarmMatch.groupValues.getOrNull(3)
            if (amPm?.uppercase() == "PM" && hour < 12) hour += 12
            if (amPm?.uppercase() == "AM" && hour == 12) hour = 0
        }

        val timerMatch = Regex("(\\d+)\\s*(?:s|sec|second|seconds)", RegexOption.IGNORE_CASE).find(content)
        if (timerMatch != null) {
            seconds = timerMatch.groupValues[1].toIntOrNull() ?: 60
        }

        val labelMatch = Regex("label(?:led)?\\s+[\"']?([^\"']+)[\"']?", RegexOption.IGNORE_CASE).find(content)
        if (labelMatch != null) {
            label = labelMatch.groupValues[1]
        }
    }

    return AlarmTimerInfoData(hour, minute, seconds, isAlarm, label)
}

fun parseCalendarEvents(content: String): List<String> {
    val lines = content.split("\n")
    val events = mutableListOf<String>()
    for (line in lines) {
        if (line.trim().startsWith("•") || line.trim().startsWith("-")) {
            events.add(line.trim().removePrefix("•").removePrefix("-").trim())
        } else if (line.contains("created:", ignoreCase = true)) {
            events.add(line.substringAfter("created:").trim())
        }
    }
    return events
}

fun parseContactSmsInfo(content: String, actionType: String?, actionData: String?): ContactSmsInfoData {
    val type = actionType ?: "CALL"
    val target = actionData ?: "Contact"
    var smsMessage = ""
    val smsMatch = Regex("message:\\s*(.*)", RegexOption.IGNORE_CASE).find(content)
    if (smsMatch != null) {
        smsMessage = smsMatch.groupValues[1]
    } else if (content.contains("sending sms", ignoreCase = true) || content.contains("sms sent", ignoreCase = true)) {
        smsMessage = content.substringAfter("message:").substringAfter("\"").substringBefore("\"").trim()
    }
    return ContactSmsInfoData(type, target, smsMessage.ifBlank { "Sent via MyAI Assistant" })
}

// ═══════════════════════════════════════════════════════
// MAIN MESSAGE BUBBLE COMPOSABLE
// ═══════════════════════════════════════════════════════
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

    // Resolve Chat Bubble Theme via entry point
    val context = LocalContext.current
    val settingsRepository = remember {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsEntryPoint::class.java
            ).settingsRepository()
        } catch (e: Exception) {
            null
        }
    }
    val themeName = settingsRepository?.chatTheme ?: "Modern Blue Gradient"
    val bubbleTheme = remember(themeName) { ChatBubbleTheme.fromString(themeName) }

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

        // Message bubble container
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            // Bubble box
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
                            isUser -> Modifier.background(brush = bubbleTheme.userBubbleBrush)
                            isAction -> Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        TertiaryContainer,
                                        TertiaryContainer.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            else -> Modifier.background(brush = bubbleTheme.aiBubbleBrush)
                        }
                    )
                    .then(
                        if (bubbleTheme.hasBorder && !isError) {
                            Modifier.border(
                                width = 1.dp,
                                color = bubbleTheme.borderColor,
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isUser) 20.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 20.dp
                                )
                            )
                        } else {
                            Modifier
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

                    // Message text split by Markdown segments
                    val segments = remember(message.content) { splitMarkdownSegments(message.content) }
                    
                    if (segments.isEmpty() && message.content.isNotEmpty()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isError -> ErrorColor
                                isUser -> bubbleTheme.userTextColor
                                else -> bubbleTheme.aiTextColor
                            },
                            lineHeight = 20.sp
                        )
                    } else {
                        segments.forEach { segment ->
                            when (segment) {
                                is MarkdownSegment.Text -> {
                                    Text(
                                        text = parseMarkdownText(
                                            segment.content,
                                            if (isUser) bubbleTheme.userTextColor else bubbleTheme.aiTextColor
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isError -> ErrorColor
                                            isUser -> bubbleTheme.userTextColor
                                            else -> bubbleTheme.aiTextColor
                                        },
                                        lineHeight = 20.sp
                                    )
                                }
                                is MarkdownSegment.CodeBlock -> {
                                    CodeBlockView(
                                        language = segment.language,
                                        code = segment.code
                                    )
                                }
                            }
                        }
                    }

                    // ═══════════════════════════════════════
                    // VISUAL ACTION CARDS
                    // ═══════════════════════════════════════
                    if (!isUser) {
                        // 1. Device Info Card
                        val isDeviceInfo = message.actionType == "DEVICE_INFO" || 
                                           message.content.contains("Battery:") || 
                                           message.content.contains("Storage:") || 
                                           message.content.contains("RAM:")
                        
                        if (isDeviceInfo) {
                            val devInfo = parseDeviceInfo(message.content)
                            if (devInfo != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                DeviceInfoCard(
                                    batteryPct = devInfo.batteryPercentage,
                                    storageUsed = devInfo.storageUsed,
                                    storageTotal = devInfo.storageTotal,
                                    ramUsed = devInfo.ramUsed,
                                    ramTotal = devInfo.ramTotal,
                                    isCharging = message.content.contains("⚡") || message.content.contains("Charging", ignoreCase = true)
                                )
                            }
                        }
                        
                        // 2. Location Card
                        val isLocation = message.actionType == "LOCATION" || message.content.contains("Coordinates:")
                        if (isLocation) {
                            val locInfo = parseLocationInfo(message.content)
                            if (locInfo != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LocationCard(
                                    address = locInfo.address,
                                    latitude = locInfo.latitude,
                                    longitude = locInfo.longitude
                                )
                            }
                        }
                        
                        // 3. Alarm / Timer Card
                        val isAlarmTimer = message.actionType == "SET_ALARM" || 
                                           message.actionType == "SET_TIMER" ||
                                           message.content.contains("Alarm set for", ignoreCase = true) ||
                                           message.content.contains("Timer set for", ignoreCase = true)
                        if (isAlarmTimer) {
                            val alarmInfo = parseAlarmTimerInfo(message.content, message.actionType, message.actionData)
                            Spacer(modifier = Modifier.height(8.dp))
                            AlarmTimerCard(
                                hour = alarmInfo.hour,
                                minute = alarmInfo.minute,
                                seconds = alarmInfo.seconds,
                                isAlarm = alarmInfo.isAlarm,
                                label = alarmInfo.label
                            )
                        }
                        
                        // 4. Calendar Card
                        val isCalendar = message.actionType == "CALENDAR" || message.content.contains("Upcoming events:", ignoreCase = true)
                        if (isCalendar) {
                            val events = parseCalendarEvents(message.content)
                            Spacer(modifier = Modifier.height(8.dp))
                            CalendarCard(events = events)
                        }
                        
                        // 5. Contact / SMS Card
                        val isContactSms = message.actionType == "CALL" || message.actionType == "SMS"
                        if (isContactSms) {
                            val info = parseContactSmsInfo(message.content, message.actionType, message.actionData)
                            Spacer(modifier = Modifier.height(8.dp))
                            ContactSmsCard(
                                type = info.type,
                                nameOrPhone = info.nameOrPhone,
                                smsBody = info.smsBody
                            )
                        }
                    }

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

// ═══════════════════════════════════════════════════════
// SUBCOMPONENTS: AI AVATAR & BADGES
// ═══════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════
// SLEEK CODE BLOCK VIEW
// ═══════════════════════════════════════════════════════
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val cleanLanguage = language.ifBlank { "code" }.uppercase()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cleanLanguage,
                    color = Color(0xFFABB2BF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFFABB2BF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    color = Color(0xFFE5C07B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// PREMIUM VISUAL CARDS
// ═══════════════════════════════════════════════════════
@Composable
fun DeviceInfoCard(
    batteryPct: Int,
    storageUsed: Float,
    storageTotal: Float,
    ramUsed: Float,
    ramTotal: Float,
    isCharging: Boolean
) {
    val batteryProgress = (batteryPct.toFloat() / 100f).coerceIn(0f, 1f)
    val storageProgress = if (storageTotal > 0) (storageUsed / storageTotal).coerceIn(0f, 1f) else 0f
    val ramProgress = if (ramTotal > 0) (ramUsed / ramTotal).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeveloperMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Battery Meter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                        CircularProgressIndicator(
                            progress = batteryProgress,
                            modifier = Modifier.fillMaxSize(),
                            color = if (batteryPct < 20) ErrorColor else SuccessColor,
                            strokeWidth = 5.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        Icon(
                            imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Battery", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("$batteryPct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }

                // Storage Meter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                        CircularProgressIndicator(
                            progress = storageProgress,
                            modifier = Modifier.fillMaxSize(),
                            color = PrimaryDark,
                            strokeWidth = 5.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Storage", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("${storageUsed.toInt()}/${storageTotal.toInt()} GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }

                // RAM Meter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                        CircularProgressIndicator(
                            progress = ramProgress,
                            modifier = Modifier.fillMaxSize(),
                            color = TertiaryDark,
                            strokeWidth = 5.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("RAM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.1f", ramUsed)}/${String.format("%.1f", ramTotal)} GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    address: String,
    latitude: Double,
    longitude: Double
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = ErrorColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Lat: ${String.format("%.5f", latitude)}, Lng: ${String.format("%.5f", longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val uri = android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($address)")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show on Google Maps", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmTimerCard(
    hour: Int,
    minute: Int,
    seconds: Int,
    isAlarm: Boolean,
    label: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151D2A)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PrimaryDark.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PrimaryDark.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlarm) Icons.Filled.Alarm else Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = PrimaryDark,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isAlarm) "Alarm Active" else "Timer Configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                val formattedTime = if (isAlarm) {
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    String.format("%02d:%02d %s", h, minute, amPm)
                } else {
                    val m = seconds / 60
                    val s = seconds % 60
                    String.format("%02d:%02d Sec", m, s)
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun CalendarCard(
    events: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Calendar Events",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (events.isEmpty()) {
                Text(
                    text = "No upcoming events found or event created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.forEachIndexed { index, event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                    if (index < events.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactSmsCard(
    type: String,
    nameOrPhone: String,
    smsBody: String
) {
    val initial = nameOrPhone.trim().firstOrNull()?.uppercase() ?: "?"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(PrimaryDark, TertiaryDark)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = nameOrPhone,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )

                Text(
                    text = if (type == "CALL") "Voice Call Command" else "SMS Message",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )

                if (type == "SMS" && smsBody.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$smsBody\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (type == "CALL") Icons.Filled.Call else Icons.Filled.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// TIME FORMATTER
// ═══════════════════════════════════════════════════════
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
