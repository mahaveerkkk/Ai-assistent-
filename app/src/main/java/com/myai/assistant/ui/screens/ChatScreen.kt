// File: app/src/main/java/com/myai/assistant/ui/screens/ChatScreen.kt
// Chat Screen — Main AI chat interface

package com.myai.assistant.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myai.assistant.ui.components.MessageBubble
import com.myai.assistant.ui.components.TypingIndicator
import com.myai.assistant.ui.components.VoiceButton
import com.myai.assistant.ui.theme.*
import com.myai.assistant.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var showMenu by remember { mutableStateOf(false) }

    // Auto-scroll jab naya message aaye
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // ═══════════════════════════════════════
        // TOP BAR
        // ═══════════════════════════════════════
        topBar = {
            ChatTopBar(
                isAiThinking = uiState.isAiThinking,
                isVoiceEnabled = uiState.isVoiceEnabled,
                aiSource = uiState.aiSource,
                onToggleVoice = { viewModel.toggleVoiceOutput() },
                showMenu = showMenu,
                onMenuToggle = { showMenu = !showMenu },
                onClearChat = {
                    viewModel.clearChat()
                    showMenu = false
                },
                onSettings = {
                    showMenu = false
                    onNavigateToSettings()
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ═══════════════════════════════════════
            // MESSAGE LIST
            // ═══════════════════════════════════════
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = uiState.messages.filter { !it.isLoading },
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        modifier = Modifier.animateItem()
                    )
                }

                // Typing indicator
                if (uiState.isAiThinking) {
                    item(key = "typing") {
                        TypingIndicator(modifier = Modifier.animateItem())
                    }
                }
            }

            // ═══════════════════════════════════════
            // INPUT BAR
            // ═══════════════════════════════════════
            ChatInputBar(
                inputText = uiState.inputText,
                isListening = uiState.isListening,
                isAiThinking = uiState.isAiThinking,
                onTextChange = { viewModel.updateInputText(it) },
                onSend = {
                    viewModel.sendMessage()
                    focusManager.clearFocus()
                },
                onVoiceClick = { viewModel.toggleListening() }
            )
        }
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Auto-dismiss after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.dismissError()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    isAiThinking: Boolean,
    isVoiceEnabled: Boolean,
    aiSource: String,
    onToggleVoice: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: () -> Unit,
    onClearChat: () -> Unit,
    onSettings: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // AI avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(GradientStart, GradientMiddle)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome, "AI",
                        tint = Color.White, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "MyAI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when {
                            isAiThinking -> "Soch raha hoon..."
                            else -> when(aiSource) {
                                "ollama" -> "🟢 Ollama Local"
                                "gemini" -> "🔵 Gemini Cloud"
                                else -> "⚪ Offline"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isAiThinking -> WarningColor
                            aiSource == "ollama" -> SuccessColor
                            aiSource == "gemini" -> PrimaryDark
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.sp
                    )
                }
            }
        },
        actions = {
            // Voice output toggle
            IconButton(onClick = onToggleVoice) {
                Icon(
                    imageVector = if (isVoiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Voice",
                    tint = if (isVoiceEnabled) PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Menu
            Box {
                IconButton(onClick = onMenuToggle) {
                    Icon(
                        Icons.Filled.MoreVert, "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle() }) {
                    DropdownMenuItem(
                        text = { Text("🗑️ Clear Chat") },
                        onClick = onClearChat
                    )
                    DropdownMenuItem(
                        text = { Text("⚙️ Settings") },
                        onClick = onSettings
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ═══════════════════════════════════════════════════════════════
// INPUT BAR — Text field + Voice + Send
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ChatInputBar(
    inputText: String,
    isListening: Boolean,
    isAiThinking: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = {
                    Text(
                        "Kuch poocho...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryDark,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) onSend() }),
                maxLines = 4
            )

            Spacer(Modifier.width(8.dp))

            // Voice / Send button
            if (inputText.isBlank()) {
                // Voice button
                VoiceButton(
                    isListening = isListening,
                    onClick = onVoiceClick,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            } else {
                // Send button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(listOf(GradientStart, GradientMiddle))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onSend,
                        enabled = !isAiThinking
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "Send",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
