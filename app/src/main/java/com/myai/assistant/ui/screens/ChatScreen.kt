// File: app/src/main/java/com/myai/assistant/ui/screens/ChatScreen.kt
// Chat Screen — Main AI chat interface

package com.myai.assistant.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.ui.components.MessageBubble
import com.myai.assistant.ui.components.TypingIndicator
import com.myai.assistant.ui.components.VoiceButton
import com.myai.assistant.ui.theme.*
import com.myai.assistant.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val continuousVoiceMode by viewModel.continuousVoiceMode.collectAsState()

    // Continuous voice mode state tracking
    var wasSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isSpeaking) {
        if (continuousVoiceMode) {
            val isSpeaking = uiState.isSpeaking
            if (wasSpeaking && !isSpeaking) {
                val lastMessage = uiState.messages.lastOrNull()
                if (lastMessage != null && lastMessage.sender == MessageSender.AI) {
                    kotlinx.coroutines.delay(500)
                    if (!uiState.isListening) {
                        viewModel.toggleListening()
                    }
                }
            }
            wasSpeaking = isSpeaking
        }
    }

    // Listening to cold start and new intents from widget
    LaunchedEffect(Unit) {
        if (viewModel.launchVoicePending) {
            viewModel.launchVoicePending = false
            if (!uiState.isListening) {
                viewModel.toggleListening()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.voiceIntentTrigger.collect {
            if (!uiState.isListening) {
                viewModel.toggleListening()
            }
        }
    }

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
                isContinuousVoiceEnabled = continuousVoiceMode,
                onToggleContinuousVoice = { viewModel.setContinuousVoiceMode(!continuousVoiceMode) },
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
                },
                onDiagnostics = {
                    showMenu = false
                    showDiagnosticsDialog = true
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendMessage()
                    focusManager.clearFocus()
                },
                onVoiceClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleListening()
                },
                onCameraClick = onNavigateToCamera,
                onQuickActionsClick = {
                    showQuickActions = true
                }
            )
        }
    }

    if (showQuickActions) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActions = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "⚡ Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Filled.FlashlightOn,
                            title = "Flashlight",
                            subtitle = if (viewModel.isFlashlightOn) "Turn Off" else "Turn On",
                            active = viewModel.isFlashlightOn,
                            onClick = {
                                val success = viewModel.toggleFlashlight()
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        QuickActionCard(
                            icon = Icons.Filled.Wifi,
                            title = "WiFi Panel",
                            subtitle = "Open Panel",
                            active = false,
                            onClick = {
                                val success = viewModel.openWifiPanel()
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showQuickActions = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Filled.DoNotDisturb,
                            title = "DND Mode",
                            subtitle = if (viewModel.isDndOn) "Turn Off" else "Turn On",
                            active = viewModel.isDndOn,
                            onClick = {
                                val success = viewModel.toggleDnd()
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        QuickActionCard(
                            icon = Icons.Filled.Bluetooth,
                            title = "Bluetooth",
                            subtitle = if (viewModel.isBluetoothOn) "Turn Off" else "Turn On",
                            active = viewModel.isBluetoothOn,
                            onClick = {
                                val success = viewModel.toggleBluetooth()
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Filled.Alarm,
                            title = "Alarm",
                            subtitle = "Show Alarms",
                            active = false,
                            onClick = {
                                val success = viewModel.openAlarm()
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showQuickActions = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        QuickActionCard(
                            icon = Icons.Filled.Screenshot,
                            title = "Screenshot",
                            subtitle = "Capture Screen",
                            active = false,
                            onClick = {
                                viewModel.takeScreenshot()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showQuickActions = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isServiceRunning = com.myai.assistant.accessibility.MyAccessibilityService.isRunning()
        var appNameInput by remember { mutableStateOf("WhatsApp") }

        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = {
                Text(
                    text = "🔧 Diagnostic Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Accessibility status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accessibility Service:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isServiceRunning) "🟢 Active" else "🔴 Inactive (Click to Turn On)",
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) SuccessColor else ErrorColor,
                            modifier = Modifier
                                .clickable {
                                    if (!isServiceRunning) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                .padding(4.dp)
                        )
                    }

                    HorizontalDivider()

                    // Global simulation buttons
                    Text("Global Gestures:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("BACK") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Back", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("HOME") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Home", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("RECENTS") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Recents", fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("NOTIFICATIONS") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Notifications", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("SCROLL_DOWN") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Scroll Down", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("SCROLL_UP") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Scroll Up", fontSize = 10.sp)
                        }
                    }
                    HorizontalDivider()

                    // System Controls section
                    Text("System Toggles:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("WIFI", "on") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("WiFi Panel", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("BLUETOOTH", "on") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("BT Toggle", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("VOLUME", "up") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Vol Up", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.triggerDiagnosticAction("VOLUME", "down") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Vol Down", fontSize = 10.sp)
                        }
                    }

                    HorizontalDivider()

                    // App Opener section
                    Text("App Launch Test:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = appNameInput,
                        onValueChange = { appNameInput = it },
                        label = { Text("App Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.triggerDiagnosticAction("OPEN_APP", appNameInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("Done")
                }
            }
        )
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
    isContinuousVoiceEnabled: Boolean,
    onToggleContinuousVoice: () -> Unit,
    aiSource: String,
    onToggleVoice: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: () -> Unit,
    onClearChat: () -> Unit,
    onSettings: () -> Unit = {},
    onDiagnostics: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // AI avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
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
                            aiSource == "gemini" -> MaterialTheme.colorScheme.primary
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
                    tint = if (isVoiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Continuous voice mode toggle
            IconButton(onClick = onToggleContinuousVoice) {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = "Continuous Voice Mode",
                    tint = if (isContinuousVoiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                    DropdownMenuItem(
                        text = { Text("🔧 Diagnostics & Test") },
                        onClick = onDiagnostics
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
    onVoiceClick: () -> Unit,
    onCameraClick: () -> Unit,
    onQuickActionsClick: () -> Unit
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
            // Quick Actions Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onQuickActionsClick) {
                    Icon(
                        Icons.Filled.Widgets,
                        contentDescription = "Quick Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) onSend() }),
                maxLines = 4
            )

            Spacer(Modifier.width(8.dp))

            // Camera Button next to the send/voice button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onCameraClick) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "Camera",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary 
                                 else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer 
                           else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
