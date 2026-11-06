package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteEntity
import com.example.data.local.LocalAudioScanner
import com.example.data.local.MusicDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistItemEntity
import com.example.data.local.RecentEntity
import com.example.data.model.Lyrics
import com.example.data.model.Song
import com.example.data.model.SongSource
import com.example.data.network.GoogleMusicSearchService
import com.example.data.network.LyricsService
import com.example.data.network.YouTubeMusicService
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val youTubeMusicService = YouTubeMusicService()
    private val googleMusicSearchService = GoogleMusicSearchService(youTubeMusicService)
    private val lyricsService = LyricsService()
    private val localAudioScanner = LocalAudioScanner(application)
    private val database = MusicDatabase.getDatabase(application)
    private val musicDao = database.musicDao()

    val playerManager = AudioPlayerManager(application, youTubeMusicService)

    // Player State delegations
    val currentSong = playerManager.currentSong
    val isPlaying = playerManager.isPlaying
    val isBuffering = playerManager.isBuffering
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs
    val queue = playerManager.queue
    val currentIndex = playerManager.currentIndex
    val shuffleEnabled = playerManager.shuffleEnabled
    val repeatMode = playerManager.repeatMode
    val volume = playerManager.volume
    val playbackSpeed = playerManager.playbackSpeed
    val errorMessage = playerManager.errorMessage

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val initialSeedTracks = listOf(
        Song(
            id = "audius_AK3w5q0",
            title = "140 Mix (Audius Exclusive)",
            artist = "iLLPeTiLL",
            album = "Online Charts",
            durationMs = 210000L,
            artworkUrl = "https://i.ytimg.com/vi/AK3w5q0/hqdefault.jpg",
            audioStreamUrl = "https://api.audius.co/v1/tracks/AK3w5q0/stream?app_name=AuraMusic",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "AK3w5q0"
        ),
        Song(
            id = "audius_5QW2Y",
            title = "Midnight City (feat. chirrrex)",
            artist = "c152",
            album = "Online Music",
            durationMs = 244000L,
            artworkUrl = "https://i.ytimg.com/vi/5QW2Y/hqdefault.jpg",
            audioStreamUrl = "https://api.audius.co/v1/tracks/5QW2Y/stream?app_name=AuraMusic",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "5QW2Y"
        ),
        Song(
            id = "audius_87GrbyW",
            title = "Star Hunter (Cinematic Synthwave)",
            artist = "Blythely",
            album = "Synthwave",
            durationMs = 195000L,
            artworkUrl = "https://i.ytimg.com/vi/87GrbyW/hqdefault.jpg",
            audioStreamUrl = "https://api.audius.co/v1/tracks/87GrbyW/stream?app_name=AuraMusic",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "87GrbyW"
        ),
        Song(
            id = "audius_Ma6qb3x",
            title = "Deadly Combo [DISKADE Bootleg]",
            artist = "DISKADE",
            album = "Electronic",
            durationMs = 180000L,
            artworkUrl = "https://i.ytimg.com/vi/Ma6qb3x/hqdefault.jpg",
            audioStreamUrl = "https://api.audius.co/v1/tracks/Ma6qb3x/stream?app_name=AuraMusic",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "Ma6qb3x"
        ),
        Song(
            id = "audius_y8rNVYP",
            title = "Atmospheric Soundscapes",
            artist = "False Dawn",
            album = "Ambient",
            durationMs = 220000L,
            artworkUrl = "https://i.ytimg.com/vi/y8rNVYP/hqdefault.jpg",
            audioStreamUrl = "https://api.audius.co/v1/tracks/y8rNVYP/stream?app_name=AuraMusic",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "y8rNVYP"
        )
    )

    // Curated Explore Tracks
    private val _curatedTracks = MutableStateFlow<List<Song>>(initialSeedTracks)
    val curatedTracks: StateFlow<List<Song>> = _curatedTracks.asStateFlow()

    private val _isLoadingCurated = MutableStateFlow(false)
    val isLoadingCurated: StateFlow<Boolean> = _isLoadingCurated.asStateFlow()

    // Local Tracks
    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    // Lyrics State
    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    // Full Player Modal Visibility
    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    // Theme Mode (true = Dark, false = Light)
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Dialogs
    private val _addToPlaylistSong = MutableStateFlow<Song?>(null)
    val addToPlaylistSong: StateFlow<Song?> = _addToPlaylistSong.asStateFlow()

    private val _isCreatePlaylistDialogOpen = MutableStateFlow(false)
    val isCreatePlaylistDialogOpen: StateFlow<Boolean> = _isCreatePlaylistDialogOpen.asStateFlow()

    // Room Flows
    val playlists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Song>> = musicDao.getAllFavorites()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSongs: StateFlow<List<Song>> = musicDao.getRecentSongs()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    private var lyricsJob: Job? = null

    init {
        // Record song to recents database when played
        playerManager.onSongPlayed = { song ->
            viewModelScope.launch {
                musicDao.recordRecent(
                    RecentEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.durationMs,
                        artworkUrl = song.artworkUrl,
                        source = song.source.name,
                        sourceId = song.sourceId
                    )
                )
            }
            fetchLyricsForSong(song)
        }

        // Load initial curated tracks
        loadCuratedTracks("Top Global Hits 2026")
    }

    fun setFullPlayerVisible(visible: Boolean) {
        _isFullPlayerVisible.value = visible
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    private val _isGoogleSearchMode = MutableStateFlow(true)
    val isGoogleSearchMode: StateFlow<Boolean> = _isGoogleSearchMode.asStateFlow()

    fun toggleGoogleSearchMode() {
        _isGoogleSearchMode.value = !_isGoogleSearchMode.value
    }

    fun loadCuratedTracks(genreOrMood: String) {
        viewModelScope.launch {
            _isLoadingCurated.value = true
            val results = googleMusicSearchService.searchMusic(genreOrMood)
            if (results.isNotEmpty()) {
                _curatedTracks.value = results
            }
            _isLoadingCurated.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        suggestionsJob?.cancel()
        if (query.trim().length >= 2) {
            suggestionsJob = viewModelScope.launch {
                delay(150)
                val suggestions = if (_isGoogleSearchMode.value) {
                    googleMusicSearchService.getGoogleSuggestions(query)
                } else {
                    youTubeMusicService.getSearchSuggestions(query)
                }
                _searchSuggestions.value = suggestions
            }
        } else {
            _searchSuggestions.value = emptyList()
        }
    }

    fun executeSearch(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchSuggestions.value = emptyList()
            val results = if (_isGoogleSearchMode.value) {
                googleMusicSearchService.searchMusic(query)
            } else {
                youTubeMusicService.searchSongs(query)
            }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun playSong(song: Song, newQueue: List<Song>? = null) {
        viewModelScope.launch {
            val isFav = musicDao.isFavoriteSync(song.id)
            playerManager.playSong(song.copy(isFavorite = isFav), newQueue)
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipToNext() = playerManager.skipToNext()
    fun skipToPrevious() = playerManager.skipToPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun cycleRepeatMode() = playerManager.cycleRepeatMode()
    fun setVolume(vol: Float) = playerManager.setVolume(vol)
    fun cyclePlaybackSpeed() {
        val next = when (playbackSpeed.value) {
            0.75f -> 1.0f
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 0.75f
        }
        playerManager.setPlaybackSpeed(next)
    }

    fun addToQueue(song: Song) = playerManager.addToQueue(song)
    fun playNext(song: Song) = playerManager.playNext(song)
    fun removeFromQueue(index: Int) = playerManager.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = playerManager.moveQueueItem(from, to)
    fun clearQueue() = playerManager.clearQueue()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFav = musicDao.isFavoriteSync(song.id)
            if (isFav) {
                musicDao.removeFavorite(song.id)
                if (currentSong.value?.id == song.id) {
                    playerManager.playSong(song.copy(isFavorite = false))
                }
            } else {
                musicDao.addFavorite(
                    FavoriteEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.durationMs,
                        artworkUrl = song.artworkUrl,
                        source = song.source.name,
                        sourceId = song.sourceId
                    )
                )
                if (currentSong.value?.id == song.id) {
                    playerManager.playSong(song.copy(isFavorite = true))
                }
            }
        }
    }

    private fun fetchLyricsForSong(song: Song) {
        lyricsJob?.cancel()
        _currentLyrics.value = null
        lyricsJob = viewModelScope.launch {
            _isLoadingLyrics.value = true
            val lyrics = lyricsService.fetchLyrics(
                trackName = song.title,
                artistName = song.artist,
                durationMs = song.durationMs
            )
            _currentLyrics.value = lyrics
            _isLoadingLyrics.value = false
        }
    }

    // Local files scanner
    fun addLocalUris(uris: List<Uri>) {
        viewModelScope.launch {
            val parsedSongs = uris.map { localAudioScanner.parseAudioUri(it) }
            val existing = _localSongs.value.toMutableList()
            existing.addAll(parsedSongs)
            _localSongs.value = existing
        }
    }

    // Playlists
    fun openAddToPlaylist(song: Song) {
        _addToPlaylistSong.value = song
    }

    fun closeAddToPlaylist() {
        _addToPlaylistSong.value = null
    }

    fun setCreatePlaylistDialogOpen(open: Boolean) {
        _isCreatePlaylistDialogOpen.value = open
    }

    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            val playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description
            )
            musicDao.insertPlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            val item = PlaylistItemEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                artworkUrl = song.artworkUrl,
                source = song.source.name,
                sourceId = song.sourceId
            )
            musicDao.insertPlaylistItem(item)
        }
    }

    fun getPlaylistSongs(playlistId: String) = musicDao.getPlaylistItems(playlistId)
        .map { items -> items.map { it.toSong() } }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicDao.deletePlaylist(playlistId)
            musicDao.clearPlaylistItems(playlistId)
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            musicDao.clearRecents()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
