// File: app/src/main/java/com/myai/assistant/ui/MainActivity.kt
// Main Activity — Hilt + Navigation + Edge-to-Edge

package com.myai.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.myai.assistant.ui.navigation.AppNavigation
import com.myai.assistant.ui.theme.MyAIAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge display enable karo (status bar ke peeche bhi content)
        enableEdgeToEdge()

        setContent {
            MyAIAssistantTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Navigation handle karega screens
                    AppNavigation()
                }
            }
        }
    }
}
