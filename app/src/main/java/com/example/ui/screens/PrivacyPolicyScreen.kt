package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.GlassWhite10
import com.example.ui.theme.GlassWhite20

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassWhite20, RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            PrivacyPolicyContent(
                onBack = onDismiss,
                isDialog = true
            )
        }
    }
}

@Composable
fun PrivacyPolicyContent(
    onBack: () -> Unit,
    isDialog: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (isDialog) 18.dp else 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Last updated: November 2026",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AccentEmerald.copy(alpha = 0.2f),
                            AccentCyan.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Zero Tracking Guarantee",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "No user telemetry, analytics trackers, or personal profile tracking.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sections
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PolicySection(
                    icon = Icons.Default.Lock,
                    title = "1. Information We Do NOT Collect",
                    content = "Aura Online Music Player does not require registration, login, or any personal account credentials. We do not collect or store your name, email address, phone number, location, or device identifiers. You can freely stream online music without creating an account."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.CloudDone,
                    title = "2. Online Audio Streaming",
                    content = "When you search for tracks or stream audio, the app queries public decentralized streaming instances (such as Invidious, Piped, and YouTube content delivery networks) to fetch media metadata and raw audio streams directly. We do not operate intermediate servers that log your listening habits."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.Security,
                    title = "3. On-Device Local Data Storage",
                    content = "Your playlists, favorites, and recent streaming history are stored 100% locally on your device within an encrypted SQLite/Room database. This data is never sent to any remote server and remains under your complete control."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.Policy,
                    title = "4. Device Permissions Explained",
                    content = "• INTERNET & ACCESS_NETWORK_STATE: Required solely to stream online music and fetch synchronized lyrics.\n• FOREGROUND_SERVICE & MEDIA_PLAYBACK: Required by Android to keep playback active when the screen is turned off or while switching apps.\n• WAKE_LOCK: Prevents the CPU from sleeping during audio playback."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.Shield,
                    title = "5. Third-Party Services (LRCLIB & YouTube)",
                    content = "Synchronized lyrics are fetched from LRCLIB (an open community-supported lyrics service) via secure HTTPS requests containing only the song title and artist. No user identification headers are transmitted."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.DeleteSweep,
                    title = "6. Data Retention & Erasure Rights",
                    content = "You can wipe your listening history, favorite tracks, and custom playlists at any time with one tap in Settings -> Clear Listening Data, or by uninstalling the application. No lingering data remains on any cloud servers."
                )
            }

            item {
                PolicySection(
                    icon = Icons.Default.Policy,
                    title = "7. Contact & Inquiries",
                    content = "If you have questions about this Privacy Policy or wish to provide feedback regarding data protection, please reach out through the application support channel. We are committed to maintaining a private, ad-free listening experience."
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
        ) {
            Text("Close Privacy Policy")
        }
    }
}

@Composable
private fun PolicySection(
    icon: ImageVector,
    title: String,
    content: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhite10)
            .border(1.dp, GlassWhite20, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
