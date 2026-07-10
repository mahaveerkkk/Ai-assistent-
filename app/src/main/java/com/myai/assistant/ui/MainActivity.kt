// File: app/src/main/java/com/myai/assistant/ui/MainActivity.kt
// Main Activity — Hilt + Navigation + Edge-to-Edge

package com.myai.assistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.myai.assistant.ui.navigation.AppNavigation
import com.myai.assistant.ui.theme.MyAIAssistantTheme
import com.myai.assistant.viewmodel.AssistantViewModel
import com.myai.assistant.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge display enable karo (status bar ke peeche bhi content)
        enableEdgeToEdge()

        handleVoiceIntent(intent)

        setContent {
            val chatTheme by viewModel.chatTheme.collectAsState()
            MyAIAssistantTheme(chatTheme = chatTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Navigation handle karega screens
                    AppNavigation(settingsRepository = settingsRepository)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("launch_voice", false) == true) {
            viewModel.launchVoicePending = true
            viewModel.triggerVoiceIntent()
        }
    }
}
