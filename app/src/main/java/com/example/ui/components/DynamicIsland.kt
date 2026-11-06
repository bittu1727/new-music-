package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Signature iOS / Dynamic Island Coral Orange Color
val DynamicIslandCoral = Color(0xFFFF5722)

@Composable
fun DynamicIsland(
    song: Song?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled || song == null) return

    var isExpanded by remember { mutableStateOf(false) }

    // Auto-pop whenever track changes: briefly expand for 3.5s to announce track
    var lastSongId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        if (lastSongId != null && lastSongId != song.id) {
            isExpanded = true
            delay(3500)
            isExpanded = false
        }
        lastSongId = song.id
    }

    val islandWidth by animateDpAsState(
        targetValue = if (isExpanded) 340.dp else 208.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )

    val islandHeight by animateDpAsState(
        targetValue = if (isExpanded) 154.dp else 37.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandHeight"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 20.dp,
        label = "cornerRadius"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .shadow(
                    elevation = if (isExpanded) 18.dp else 8.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black,
                    spotColor = DynamicIslandCoral.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black)
                .border(
                    width = 0.8.dp,
                    color = Color(0x38FFFFFF),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isExpanded = !isExpanded
                }
                .testTag(if (isExpanded) "dynamic_island_expanded" else "dynamic_island_pill"),
            color = Color.Black,
            shape = RoundedCornerShape(cornerRadius)
        ) {
            if (isExpanded) {
                DynamicIslandExpandedContent(
                    song = song,
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onOpenFullPlayer = onOpenFullPlayer,
                    onCollapse = { isExpanded = false }
                )
            } else {
                DynamicIslandScreenshotMatchContent(
                    song = song,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

/**
 * Pixel-accurate implementation of the user's uploaded reference screenshot:
 * - Jet black capsule pill
 * - Squircle/rounded-square album cover on the left
 * - Pure black empty center
 * - 3 vertical bars + 2 dots in glowing Coral Orange on the right
 */
@Composable
private fun DynamicIslandScreenshotMatchContent(
    song: Song,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Squircle Album Artwork (exact match to screenshot)
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161616))
                .border(0.6.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!song.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = DynamicIslandCoral,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Center: Pure sleek black notch space
        Spacer(modifier = Modifier.weight(1f))

        // Right: 3 vertical bars + 2 dots in vibrant coral orange
        IosDynamicIslandWaveform(
            isPlaying = isPlaying,
            modifier = Modifier.padding(end = 4.dp),
            color = DynamicIslandCoral
        )
    }
}

/**
 * Signature iOS Waveform: 3 vertical rounded bars + 2 dots
 */
@Composable
fun IosDynamicIslandWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = DynamicIslandCoral
) {
    val bar1Anim = remember { Animatable(0.55f) }
    val bar2Anim = remember { Animatable(0.92f) }
    val bar3Anim = remember { Animatable(0.65f) }
    val dotAlphaAnim = remember { Animatable(1f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            launch {
                bar1Anim.animateTo(
                    targetValue = 0.28f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(260, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            launch {
                bar2Anim.animateTo(
                    targetValue = 0.42f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(330, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            launch {
                bar3Anim.animateTo(
                    targetValue = 0.32f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(290, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            launch {
                dotAlphaAnim.animateTo(
                    targetValue = 0.65f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(420, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            bar1Anim.animateTo(0.22f, tween(200))
            bar2Anim.animateTo(0.22f, tween(200))
            bar3Anim.animateTo(0.22f, tween(200))
            dotAlphaAnim.animateTo(0.45f, tween(200))
        }
    }

    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        // Bar 1 (medium)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((16 * bar1Anim.value).coerceAtLeast(3f).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
        // Bar 2 (tallest)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((20 * bar2Anim.value).coerceAtLeast(3f).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
        // Bar 3 (medium-tall)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((17 * bar3Anim.value).coerceAtLeast(3f).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
        // Dot 1
        Box(
            modifier = Modifier
                .size(3.5.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = dotAlphaAnim.value))
        )
        // Dot 2
        Box(
            modifier = Modifier
                .size(3.5.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = dotAlphaAnim.value))
        )
    }
}

@Composable
private fun DynamicIslandExpandedContent(
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Row: Squircle Album art, Title, Artist, Waveform & Collapse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A22))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { onOpenFullPlayer() },
                contentAlignment = Alignment.Center
            ) {
                if (!song.artworkUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = DynamicIslandCoral,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Song Info (Clickable to open player)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenFullPlayer() }
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right icons: Coral Waveform & Collapse button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IosDynamicIslandWaveform(
                    isPlaying = isPlaying,
                    color = DynamicIslandCoral
                )

                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Collapse Island",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Middle Row: Scrubbing progress bar in matching Coral Orange
        val progressFraction = if (durationMs > 0) {
            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = DynamicIslandCoral,
                trackColor = Color.White.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(progressMs),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
                Text(
                    text = formatMs(durationMs),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }

        // Bottom Row: Media Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Full Player Expand Icon
            IconButton(
                onClick = onOpenFullPlayer,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "Open Full Player",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Central playback controls: Previous, Play/Pause, Next
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DynamicIslandCoral)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Song",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Balanced spacing spacer
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
