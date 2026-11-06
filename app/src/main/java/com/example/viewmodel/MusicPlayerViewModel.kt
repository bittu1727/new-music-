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
            id = "saavn_apna_bana_le",
            title = "Apna Bana Le",
            artist = "Arijit Singh, Sachin-Jigar",
            album = "Trending Hits",
            durationMs = 261000L,
            artworkUrl = "https://c.saavncdn.com/675/Trending-Love-Songs-Hindi-2022-20221208074614-500x500.jpg",
            audioStreamUrl = "https://aac.saavncdn.com/675/1456dc2903d0fb4465d2d2904008803a_320.mp4",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "apna_bana_le"
        ),
        Song(
            id = "saavn_kesariya",
            title = "Kesariya",
            artist = "Pritam, Arijit Singh",
            album = "Brahmastra",
            durationMs = 268000L,
            artworkUrl = "https://c.saavncdn.com/871/Brahmastra-Original-Motion-Picture-Soundtrack-Hindi-2022-20221006180327-500x500.jpg",
            audioStreamUrl = "https://aac.saavncdn.com/871/c2febd353f3a076a406fa37510f31f9f_320.mp4",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "kesariya"
        ),
        Song(
            id = "saavn_believer",
            title = "Believer",
            artist = "Imagine Dragons",
            album = "Evolve",
            durationMs = 204000L,
            artworkUrl = "https://c.saavncdn.com/078/AiSh-The-Covers-Collection-Hindi-2022-20260710184759-500x500.jpg",
            audioStreamUrl = "https://aac.saavncdn.com/078/691b7984c2ad4ea849a335c966b84fe2_320.mp4",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "believer"
        ),
        Song(
            id = "saavn_shape_of_you",
            title = "Shape Of You",
            artist = "Ed Sheeran",
            album = "Divide",
            durationMs = 233000L,
            artworkUrl = "https://c.saavncdn.com/443/Shape-Of-You-English-2023-20230504142417-500x500.jpg",
            audioStreamUrl = "https://aac.saavncdn.com/443/d9b115620457a6ddfda9955ac17142c8_320.mp4",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "shape_of_you"
        ),
        Song(
            id = "saavn_blinding_lights",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            artworkUrl = "https://c.saavncdn.com/764/Blinding-Lights-English-2020-20200424164319-500x500.jpg",
            audioStreamUrl = "https://aac.saavncdn.com/764/415b88a7ed47b126e5f1806789929fb7_320.mp4",
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "blinding_lights"
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
