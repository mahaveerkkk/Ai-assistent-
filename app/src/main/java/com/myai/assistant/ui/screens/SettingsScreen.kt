// File: app/src/main/java/com/myai/assistant/ui/screens/SettingsScreen.kt
// Settings Screen — AI config, voice, overlay, about

package com.myai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.assistant.overlay.OverlayService
import com.myai.assistant.service.AssistantForegroundService
import com.myai.assistant.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.myai.assistant.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val settings = viewModel.settings

    // States initialized from settings repository
    var ollamaUrl by remember { mutableStateOf(settings.ollamaUrl) }
    var ollamaModel by remember { mutableStateOf(settings.ollamaModel) }
    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var useOllama by remember { mutableStateOf(settings.useOllama) }
    var useGemini by remember { mutableStateOf(settings.useGemini) }
    var useLiteRt by remember { mutableStateOf(settings.useLiteRt) }
    var liteRtModelPath by remember { mutableStateOf(settings.liteRtModelPath) }
    var voiceEnabled by remember { mutableStateOf(settings.voiceEnabled) }
    var floatingBubble by remember { mutableStateOf(settings.floatingBubble) }
    var autoStartBoot by remember { mutableStateOf(settings.autoStartBoot) }
    var backgroundService by remember { mutableStateOf(settings.backgroundService) }
    var chatTheme by remember { mutableStateOf(settings.chatTheme) }
    var continuousVoiceMode by remember { mutableStateOf(settings.continuousVoiceMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("⚙️ Settings", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════════════════════════════════════
            // AI ENGINE SECTION
            // ═══════════════════════════════════════
            SettingsSection(title = "🧠 AI Engine") {
                SettingsToggle(
                    icon = Icons.Filled.Memory,
                    title = "LiteRT (On-Device AI)",
                    subtitle = "Gemma/Qwen offline (Zero latency)",
                    checked = useLiteRt,
                    onToggle = {
                        useLiteRt = it
                        settings.useLiteRt = it
                        viewModel.settings.useLiteRt = it
                    }
                )

                if (useLiteRt) {
                    SettingsTextField(
                        label = "Model file path (.litertlm)",
                        value = liteRtModelPath,
                        onChange = {
                            liteRtModelPath = it
                            settings.liteRtModelPath = it
                            viewModel.settings.liteRtModelPath = it
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SettingsToggle(
                    icon = Icons.Filled.Computer,
                    title = "Ollama (Local)",
                    subtitle = "Free, private, fast",
                    checked = useOllama,
                    onToggle = {
                        useOllama = it
                        viewModel.setUseOllama(it)
                    }
                )

                if (useOllama) {
                    SettingsTextField(
                        label = "Ollama Server URL",
                        value = ollamaUrl,
                        onChange = {
                            ollamaUrl = it
                            viewModel.setOllamaUrl(it)
                        }
                    )
                    SettingsTextField(
                        label = "Model Name",
                        value = ollamaModel,
                        onChange = {
                            ollamaModel = it
                            viewModel.setOllamaModel(it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SettingsToggle(
                    icon = Icons.Filled.Cloud,
                    title = "Gemini (Cloud)",
                    subtitle = "Google AI fallback",
                    checked = useGemini,
                    onToggle = {
                        useGemini = it
                        viewModel.setUseGemini(it)
                    }
                )

                if (useGemini) {
                    SettingsTextField(
                        label = "Gemini API Key",
                        value = geminiApiKey,
                        onChange = {
                            geminiApiKey = it
                            viewModel.setGeminiApiKey(it)
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // VOICE SECTION
            // ═══════════════════════════════════════
            SettingsSection(title = "🎤 Voice") {
                SettingsToggle(
                    icon = Icons.Filled.VolumeUp,
                    title = "Voice Response",
                    subtitle = "AI jawab bole",
                    checked = voiceEnabled,
                    onToggle = {
                        voiceEnabled = it
                        viewModel.toggleVoiceOutput()
                    }
                )
                SettingsToggle(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "Continuous Voice Mode",
                    subtitle = "Automatically listen after AI finishes speaking",
                    checked = continuousVoiceMode,
                    onToggle = {
                        continuousVoiceMode = it
                        viewModel.setContinuousVoiceMode(it)
                    }
                )
            }

            // ═══════════════════════════════════════
            // APPEARANCE SECTION
            // ═══════════════════════════════════════
            SettingsSection(title = "🎨 Appearance") {
                var showThemeDropdown by remember { mutableStateOf(false) }
                val themes = listOf("Modern Blue", "Cyberpunk Purple", "Minimalist Dark", "Forest Green")
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Palette, "Theme",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chat Theme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(chatTheme, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Box {
                        TextButton(onClick = { showThemeDropdown = true }) {
                            Text("Change")
                        }
                        DropdownMenu(
                            expanded = showThemeDropdown,
                            onDismissRequest = { showThemeDropdown = false }
                        ) {
                            themes.forEach { theme ->
                                DropdownMenuItem(
                                    text = { Text(theme) },
                                    onClick = {
                                        chatTheme = theme
                                        viewModel.setChatTheme(theme)
                                        showThemeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // OVERLAY & SERVICES
            // ═══════════════════════════════════════
            SettingsSection(title = "🫧 Overlay & Services") {
                SettingsToggle(
                    icon = Icons.Filled.BubbleChart,
                    title = "Floating Bubble",
                    subtitle = "Har screen pe AI button",
                    checked = floatingBubble,
                    onToggle = {
                        floatingBubble = it
                        settings.floatingBubble = it
                        if (it) OverlayService.start(context)
                        else OverlayService.stop(context)
                    }
                )

                SettingsToggle(
                    icon = Icons.Filled.PlayArrow,
                    title = "Background Service",
                    subtitle = "AI hamesha active rahe",
                    checked = backgroundService,
                    onToggle = {
                        backgroundService = it
                        settings.backgroundService = it
                        if (it) AssistantForegroundService.start(context)
                        else AssistantForegroundService.stop(context)
                    }
                )

                SettingsToggle(
                    icon = Icons.Filled.RestartAlt,
                    title = "Auto-Start on Boot",
                    subtitle = "Phone restart pe auto-start",
                    checked = autoStartBoot,
                    onToggle = {
                        autoStartBoot = it
                        settings.autoStartBoot = it
                        context.getSharedPreferences("ai_prefs", 0)
                            .edit().putBoolean("auto_start_on_boot", it).apply()
                    }
                )
            }

            // ═══════════════════════════════════════
            // ABOUT SECTION
            // ═══════════════════════════════════════
            SettingsSection(title = "ℹ️ About") {
                SettingsInfo("App", "MyAI Assistant")
                SettingsInfo("Version", "1.0.0")
                SettingsInfo("AI Models", "LiteRT (Gemma/Qwen) + Ollama + Gemini")
                SettingsInfo("Developer", "Built with ❤️")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════
// REUSABLE COMPONENTS
// ═══════════════════════════════════════

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryDark)
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
