// File: app/src/main/java/com/myai/assistant/ui/components/TypingIndicator.kt
package com.myai.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.myai.assistant.ui.theme.*

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(
                brush = Brush.linearGradient(listOf(GradientStart, GradientMiddle)),
                shape = RoundedCornerShape(10.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, "AI", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
                .background(AiBubble).padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BouncingDot(0); BouncingDot(150); BouncingDot(300)
            }
        }
    }
}

@Composable
private fun BouncingDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "dot$delayMs")
    val offsetY by transition.animateFloat(0f, -8f, infiniteRepeatable(
        keyframes { durationMillis = 1200; 0f at delayMs; -8f at delayMs + 300; 0f at delayMs + 600 },
        RepeatMode.Restart
    ), label = "b$delayMs")
    val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(
        keyframes { durationMillis = 1200; 0.4f at delayMs; 1f at delayMs + 300; 0.4f at delayMs + 600 },
        RepeatMode.Restart
    ), label = "a$delayMs")
    Box(Modifier.size(8.dp).graphicsLayer { translationY = offsetY }.background(PrimaryDark.copy(alpha), CircleShape))
}
