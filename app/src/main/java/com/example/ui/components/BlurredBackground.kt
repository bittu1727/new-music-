package com.example.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentViolet

@Composable
fun BlurredAlbumArtBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = artworkUrl,
            animationSpec = tween(700),
            label = "albumArtCrossfade"
        ) { url ->
            if (!url.isNullOrEmpty()) {
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier
                        .fillMaxSize()
                        .blur(50.dp)
                } else {
                    Modifier.fillMaxSize()
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = blurModifier
                )
            } else {
                // Ambient fallback gradient mesh
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        AccentViolet.copy(alpha = 0.35f),
                                        AccentCyan.copy(alpha = 0.15f),
                                        Color(0xFF090A10)
                                    )
                                } else {
                                    listOf(
                                        AccentViolet.copy(alpha = 0.2f),
                                        AccentCyan.copy(alpha = 0.1f),
                                        Color(0xFFF1F5F9)
                                    )
                                }
                            )
                        )
                )
            }
        }

        // Overlay scrim with subtle radial gradient to ensure controls & text pop
        val scrimColors = if (isDarkTheme) {
            listOf(
                Color(0x99090A10),
                Color(0xCC090A10),
                Color(0xF0090A10)
            )
        } else {
            listOf(
                Color(0xB3FFFFFF),
                Color(0xEBFFFFFF),
                Color(0xF5FFFFFF)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(scrimColors))
        )
    }
}
