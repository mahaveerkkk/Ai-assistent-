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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // States
    var ollamaUrl by remember { mutableStateOf("http://10.0.2.2:11434") }
    var ollamaModel by remember { mutableStateOf("llama3") }
    var geminiApiKey by remember { mutableStateOf(com.myai.assistant.BuildConfig.GEMINI_API_KEY) }
    var useOllama by remember { mutableStateOf(true) }
    var useGemini by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var floatingBubble by remember { mutableStateOf(false) }
    var autoStartBoot by remember { mutableStateOf(true) }
    var backgroundService by remember { mutableStateOf(false) }

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
                    icon = Icons.Filled.Computer,
                    title = "Ollama (Local)",
                    subtitle = "Free, private, fast",
                    checked = useOllama,
                    onToggle = { useOllama = it }
                )

                if (useOllama) {
                    SettingsTextField(
                        label = "Ollama Server URL",
                        value = ollamaUrl,
                        onChange = { ollamaUrl = it }
                    )
                    SettingsTextField(
                        label = "Model Name",
                        value = ollamaModel,
                        onChange = { ollamaModel = it }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SettingsToggle(
                    icon = Icons.Filled.Cloud,
                    title = "Gemini (Cloud)",
                    subtitle = "Google AI fallback",
                    checked = useGemini,
                    onToggle = { useGemini = it }
                )

                if (useGemini) {
                    SettingsTextField(
                        label = "Gemini API Key",
                        value = geminiApiKey,
                        onChange = { geminiApiKey = it }
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
                    onToggle = { voiceEnabled = it }
                )
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
                SettingsInfo("AI Models", "Ollama + Gemini 2.0 Flash")
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
