package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentViolet
import kotlinx.coroutines.delay
import kotlin.random.Random

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
        targetValue = if (isExpanded) 340.dp else 190.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )

    val islandHeight by animateDpAsState(
        targetValue = if (isExpanded) 152.dp else 38.dp,
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
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .shadow(
                    elevation = if (isExpanded) 16.dp else 8.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black,
                    spotColor = AccentViolet.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFF0A0A10))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isExpanded) 0.25f else 0.18f),
                            AccentViolet.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isExpanded = !isExpanded
                }
                .testTag(if (isExpanded) "dynamic_island_expanded" else "dynamic_island_pill"),
            color = Color(0xFF0A0A10),
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
                DynamicIslandCompactContent(
                    song = song,
                    isPlaying = isPlaying,
                    onTogglePlayPause = onTogglePlayPause
                )
            }
        }
    }
}

@Composable
private fun DynamicIslandCompactContent(
    song: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit
) {
    // Subtle rotation transition for thumbnail when playing
    val infiniteTransition = rememberInfiniteTransition(label = "rotationTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Mini circular spinning artwork
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E28))
                .border(0.8.dp, AccentViolet.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!song.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .rotate(if (isPlaying) rotation else 0f)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Center: Track title & compact waveform
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        // Right: Mini dynamic waveform + micro play/pause
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniEqualizerBars(
                isPlaying = isPlaying,
                modifier = Modifier.height(14.dp)
            )

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
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
        // Top Row: Album art, Title, Artist, Collapse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E28))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
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
                        modifier = Modifier.size(46.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = AccentViolet,
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

            // Right icons: Waveform & Collapse button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniEqualizerBars(
                    isPlaying = isPlaying,
                    modifier = Modifier.height(18.dp)
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

        // Middle Row: Scrubbing progress bar
        val progressFraction = if (durationMs > 0) {
            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentViolet,
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
                        .background(
                            Brush.linearGradient(
                                listOf(AccentViolet, AccentPink)
                            )
                        )
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

@Composable
fun MiniEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 3
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val colors = listOf(AccentCyan, AccentPink, AccentViolet)
        for (i in 0 until barCount) {
            val animHeight = remember { Animatable(0.2f) }

            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    val target = 0.35f + Random.nextFloat() * 0.65f
                    val duration = 220 + i * 110
                    animHeight.animateTo(
                        targetValue = target,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                } else {
                    animHeight.animateTo(0.2f, tween(200))
                }
            }

            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((18 * animHeight.value).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors[i % colors.size])
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
