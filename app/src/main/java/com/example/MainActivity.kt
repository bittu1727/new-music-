package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PrivacyPolicyDialog
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AuraMusicTheme
import com.example.ui.theme.GlassWhite20
import com.example.viewmodel.MusicPlayerViewModel

enum class MainDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, "nav_search"),
    LIBRARY("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_library"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            AuraMusicTheme(darkTheme = isDarkTheme) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MusicPlayerViewModel) {
    var currentDestination by remember { mutableStateOf(MainDestination.HOME) }

    // Collect ViewModel States
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val isGoogleSearchMode by viewModel.isGoogleSearchMode.collectAsState()

    val curatedTracks by viewModel.curatedTracks.collectAsState()
    val isLoadingCurated by viewModel.isLoadingCurated.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val lyrics by viewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val addToPlaylistSong by viewModel.addToPlaylistSong.collectAsState()
    val isCreatePlaylistDialogOpen by viewModel.isCreatePlaylistDialogOpen.collectAsState()
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Floating MiniPlayerBar
                    MiniPlayerBar(
                        song = currentSong,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        progressMs = currentPositionMs,
                        durationMs = durationMs,
                        isFavorite = currentSong?.let { song -> favorites.any { it.id == song.id } } ?: false,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipToNext() },
                        onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                        onClick = { viewModel.setFullPlayerVisible(true) }
                    )

                    // Modern M3 NavigationBar
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassWhite20, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    ) {
                        MainDestination.values().forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = AccentViolet,
                                    indicatorColor = AccentViolet,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(destination.testTag)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    MainDestination.HOME -> {
                        HomeScreen(
                            curatedTracks = curatedTracks,
                            isLoadingCurated = isLoadingCurated,
                            recentSongs = recentSongs,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { viewModel.openAddToPlaylist(it) },
                            onSelectMood = { viewModel.loadCuratedTracks(it) }
                        )
                    }
                    MainDestination.SEARCH -> {
                        SearchScreen(
                            query = searchQuery,
                            searchResults = searchResults,
                            isSearching = isSearching,
                            suggestions = searchSuggestions,
                            isGoogleSearch = isGoogleSearchMode,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onSearch = { viewModel.executeSearch(it) },
                            onToggleGoogleSearch = { viewModel.toggleGoogleSearchMode() },
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { viewModel.openAddToPlaylist(it) }
                        )
                    }
                    MainDestination.LIBRARY -> {
                        LibraryScreen(
                            localSongs = localSongs,
                            favorites = favorites,
                            playlists = playlists,
                            recentSongs = recentSongs,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onImportLocalUris = { viewModel.addLocalUris(it) },
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { viewModel.openAddToPlaylist(it) },
                            onCreatePlaylist = { viewModel.setCreatePlaylistDialogOpen(true) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            getPlaylistSongs = { viewModel.getPlaylistSongs(it) }
                        )
                    }
                    MainDestination.SETTINGS -> {
                        SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { viewModel.toggleTheme() },
                            onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                            onClearListeningData = { viewModel.clearListeningHistory() }
                        )
                    }
                }
            }
        }

        // Full-screen player modal overlay
        AnimatedVisibility(
            visible = isFullPlayerVisible && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullPlayerModal(
                song = currentSong,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                progressMs = currentPositionMs,
                durationMs = durationMs,
                queue = queue,
                currentIndex = currentIndex,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                volume = volume,
                playbackSpeed = playbackSpeed,
                isFavorite = currentSong?.let { song -> favorites.any { it.id == song.id } } ?: false,
                lyrics = lyrics,
                isLoadingLyrics = isLoadingLyrics,
                errorMessage = errorMessage,
                onDismiss = { viewModel.setFullPlayerVisible(false) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSeekTo = { viewModel.seekTo(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onSetVolume = { viewModel.setVolume(it) },
                onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
                onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                onAddToPlaylist = { currentSong?.let { viewModel.openAddToPlaylist(it) } },
                onQueueSongClick = { idx ->
                    if (idx in queue.indices) {
                        viewModel.playSong(queue[idx])
                    }
                },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveQueueItem = { idx -> viewModel.removeFromQueue(idx) },
                onClearQueue = { viewModel.clearQueue() }
            )
        }

        // Dialog: Create New Playlist
        if (isCreatePlaylistDialogOpen) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.setCreatePlaylistDialogOpen(false) },
                onCreate = { name, desc -> viewModel.createPlaylist(name, desc) }
            )
        }

        // Dialog: Add Track to Playlist
        if (addToPlaylistSong != null) {
            AddToPlaylistDialog(
                song = addToPlaylistSong!!,
                playlists = playlists,
                onDismiss = { viewModel.closeAddToPlaylist() },
                onAddToPlaylist = { playlistId ->
                    viewModel.addSongToPlaylist(playlistId, addToPlaylistSong!!)
                },
                onCreateNewPlaylist = {
                    viewModel.setCreatePlaylistDialogOpen(true)
                }
            )
        }

        // Dialog: Privacy Policy
        if (showPrivacyPolicy) {
            PrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicy = false }
            )
        }
    }
}
