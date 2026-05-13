// File: app/src/main/java/com/myai/assistant/ui/screens/PermissionScreen.kt
// Permission Screen — Beautiful UI for requesting ALL permissions

package com.myai.assistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myai.assistant.permissions.PermissionGroup
import com.myai.assistant.permissions.PermissionManager
import com.myai.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    // ═══════════════════════════════════════
    // Permission groups
    // ═══════════════════════════════════════
    val runtimeGroups = remember { PermissionManager.getRuntimePermissionGroups() }
    val specialGroups = remember { PermissionManager.getSpecialPermissionGroups() }

    // ═══════════════════════════════════════
    // Refresh trigger — jab Settings se wapas aaye
    // ═══════════════════════════════════════
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Jab app resume ho (Settings se wapas aaye) toh refresh karo
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ═══════════════════════════════════════
    // Overall progress calculate karo
    // ═══════════════════════════════════════
    val overallProgress by remember(refreshKey) {
        derivedStateOf { PermissionManager.getOverallProgress(context) }
    }

    val criticalGranted by remember(refreshKey) {
        derivedStateOf { PermissionManager.areCriticalPermissionsGranted(context) }
    }

    // ═══════════════════════════════════════
    // Multiple permissions launcher
    // ═══════════════════════════════════════
    var currentRequestGroup by remember { mutableStateOf<PermissionGroup?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Permissions ka result aaya — refresh karo
        refreshKey++
    }

    // ═══════════════════════════════════════
    // Grant All launcher
    // ═══════════════════════════════════════
    val allPermissions = remember {
        runtimeGroups.flatMap { it.permissions }.distinct().toTypedArray()
    }

    val grantAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshKey++
    }

    // ═══════════════════════════════════════
    // Animated progress
    // ═══════════════════════════════════════
    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // Pulsing glow animation for the AI icon
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // ═══════════════════════════════════════
    // UI
    // ═══════════════════════════════════════
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════════════════════════════════
            // HEADER — AI Icon + Welcome
            // ═══════════════════════════════════════
            item {
                PermissionHeader(
                    progress = animatedProgress,
                    glowAlpha = glowAlpha
                )
            }

            // ═══════════════════════════════════════
            // GRANT ALL BUTTON
            // ═══════════════════════════════════════
            item {
                GrantAllButton(
                    progress = overallProgress,
                    onClick = {
                        grantAllLauncher.launch(allPermissions)
                    }
                )
            }

            // ═══════════════════════════════════════
            // SECTION: Runtime Permissions
            // ═══════════════════════════════════════
            item {
                SectionTitle(
                    title = "📋 Runtime Permissions",
                    subtitle = "Tap karke allow karo"
                )
            }

            items(runtimeGroups, key = { it.name }) { group ->
                val (granted, total) = remember(refreshKey) {
                    PermissionManager.getGroupStatus(context, group)
                }

                PermissionCard(
                    group = group,
                    grantedCount = granted,
                    totalCount = total,
                    onClick = {
                        if (granted < total) {
                            // Un-granted permissions request karo
                            val ungrantedPerms = group.permissions.filter {
                                !PermissionManager.isPermissionGranted(context, it)
                            }.toTypedArray()

                            if (ungrantedPerms.isNotEmpty()) {
                                permissionLauncher.launch(ungrantedPerms)
                            }
                        }
                    }
                )
            }

            // ═══════════════════════════════════════
            // SECTION: Special Permissions
            // ═══════════════════════════════════════
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle(
                    title = "⚙️ Special Permissions",
                    subtitle = "Settings mein jaake ON karo"
                )
            }

            items(specialGroups, key = { it.name }) { group ->
                val isGranted = remember(refreshKey) {
                    PermissionManager.isSpecialPermissionGranted(context, group.specialType!!)
                }

                SpecialPermissionCard(
                    group = group,
                    isGranted = isGranted,
                    onClick = {
                        if (!isGranted) {
                            val intent = PermissionManager.getSpecialPermissionIntent(
                                context, group.specialType!!
                            )
                            context.startActivity(intent)
                        }
                    }
                )
            }

            // ═══════════════════════════════════════
            // CONTINUE BUTTON
            // ═══════════════════════════════════════
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ContinueButton(
                    enabled = criticalGranted,
                    progress = overallProgress,
                    onClick = onAllPermissionsGranted
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HEADER COMPONENT
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PermissionHeader(
    progress: Float,
    glowAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // AI Icon with glow effect
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow circle
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GradientStart.copy(alpha = 0.4f),
                                GradientMiddle.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MyAI Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Kaam karne ke liye kuch permissions chahiye",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress Bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Permissions Progress",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 1f) SuccessColor else PrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom gradient progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                            )
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// GRANT ALL BUTTON
// ═══════════════════════════════════════════════════════════════
@Composable
private fun GrantAllButton(
    progress: Float,
    onClick: () -> Unit
) {
    if (progress < 1f) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(GradientStart, GradientMiddle)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sabhi Permissions Allow Karo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SECTION TITLE
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// RUNTIME PERMISSION CARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PermissionCard(
    group: PermissionGroup,
    grantedCount: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    val isFullyGranted = grantedCount == totalCount
    val isPartial = grantedCount > 0 && grantedCount < totalCount

    val cardColor = when {
        isFullyGranted -> SuccessColor.copy(alpha = 0.08f)
        isPartial -> WarningColor.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = when {
        isFullyGranted -> SuccessColor.copy(alpha = 0.3f)
        isPartial -> WarningColor.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isFullyGranted) { onClick() }
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Text(
                text = group.icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 14.dp)
            )

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                // Permission count
                Text(
                    text = "$grantedCount / $totalCount permissions",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFullyGranted) SuccessColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Status icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when {
                            isFullyGranted -> SuccessColor.copy(alpha = 0.15f)
                            isPartial -> WarningColor.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isFullyGranted -> Icons.Filled.CheckCircle
                        isPartial -> Icons.Filled.Warning
                        else -> Icons.Outlined.Lock
                    },
                    contentDescription = null,
                    tint = when {
                        isFullyGranted -> SuccessColor
                        isPartial -> WarningColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SPECIAL PERMISSION CARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SpecialPermissionCard(
    group: PermissionGroup,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val cardColor = if (isGranted) {
        SuccessColor.copy(alpha = 0.08f)
    } else {
        ErrorColor.copy(alpha = 0.06f)
    }

    val borderColor = if (isGranted) {
        SuccessColor.copy(alpha = 0.3f)
    } else {
        ErrorColor.copy(alpha = 0.2f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted) { onClick() }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Text(
                text = group.icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 14.dp)
            )

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!isGranted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        // "Required" badge
                        Box(
                            modifier = Modifier
                                .background(
                                    ErrorColor.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REQUIRED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorColor
                            )
                        }
                    }
                }
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Status / Action
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SuccessColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = SuccessColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // "Open Settings" button
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PrimaryDark.copy(alpha = 0.15f),
                        contentColor = PrimaryDark
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Open",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CONTINUE BUTTON
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ContinueButton(
    enabled: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) SuccessColor else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.RocketLaunch else Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (enabled) "Chalo Shuru Karte Hain! 🚀" else "Pehle Zaroori Permissions Do",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        if (!enabled) {
            Text(
                text = "Microphone aur Accessibility Service zaroori hai",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
