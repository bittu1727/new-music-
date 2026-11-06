package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Lyrics
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentViolet

@Composable
fun SyncedLyricsView(
    lyrics: Lyrics?,
    isLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AccentViolet,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching synced lyrics...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
            lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainLyrics.isNullOrEmpty()) -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "No lyrics found for this song",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lyrics are powered by LRCLIB community database",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            lyrics.isSynced && lyrics.lines.isNotEmpty() -> {
                val activeIndex = lyrics.getCurrentLineIndex(currentPositionMs)
                val listState = rememberLazyListState()

                // Smoothly keep the active lyric line centered
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
                        listState.animateScrollToItem(
                            index = (activeIndex - 2).coerceAtLeast(0),
                            scrollOffset = 0
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 120.dp, horizontal = 24.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val isNear = Math.abs(index - activeIndex) <= 1

                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.05f else 0.98f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "lyricScale"
                        )

                        val alpha by animateFloatAsState(
                            targetValue = when {
                                isActive -> 1f
                                isNear -> 0.65f
                                else -> 0.28f
                            },
                            animationSpec = tween(250),
                            label = "lyricAlpha"
                        )

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isActive -> Color.White
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                            },
                            animationSpec = tween(250),
                            label = "lyricColor"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSeekTo(line.timestampMs)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = line.text.ifEmpty { "♪" },
                                color = textColor,
                                fontSize = if (isActive) 22.sp else 18.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = 28.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            !lyrics.plainLyrics.isNullOrEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 40.dp, horizontal = 28.dp)
                ) {
                    item {
                        Text(
                            text = "Plain Lyrics",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = lyrics.plainLyrics,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            lineHeight = 26.sp
                        )
                    }
                }
            }
        }
    }
}
