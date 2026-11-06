package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import com.example.data.model.SongSource
import com.example.data.network.YouTubeMusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class AudioPlayerManager(
    private val context: Context,
    private val youTubeMusicService: YouTubeMusicService
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setKeepPostFor302Redirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(25_000)

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(DefaultDataSource.Factory(context, httpDataSourceFactory))

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var progressJob: Job? = null
    private var originalQueue = mutableListOf<Song>()

    // Callback on track change for history recording
    var onSongPlayed: ((Song) -> Unit)? = null

    private var retryCount = 0
    private var mediaSession: androidx.media3.session.MediaSession? = null
    private val notificationManager = MusicNotificationManager(context) { mediaSession }

    init {
        MusicPlaybackService.playerManager = this
        MusicPlaybackService.notificationManager = notificationManager

        try {
            mediaSession = androidx.media3.session.MediaSession.Builder(context, exoPlayer).build()
        } catch (e: Exception) {
            Log.w("AudioPlayerManager", "MediaSession initialization: ${e.message}")
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        retryCount = 0
                        val dur = exoPlayer.duration
                        if (dur > 0) {
                            _durationMs.value = dur
                            // Update current song duration if it was 0
                            val curr = _currentSong.value
                            if (curr != null && curr.durationMs <= 0) {
                                _currentSong.value = curr.copy(durationMs = dur)
                            }
                        }
                        notificationManager.updateNotification(_currentSong.value, _isPlaying.value)
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                }
                notificationManager.updateNotification(_currentSong.value, playing)
                MusicPlaybackService.startOrUpdateService(context, _currentSong.value, playing)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("AudioPlayerManager", "Player error: ${error.errorCodeName} - ${error.message}", error)
                _isBuffering.value = false
                val curr = _currentSong.value
                if (curr != null && (curr.source == SongSource.YOUTUBE || curr.source == SongSource.YOUTUBE_MUSIC || curr.source == SongSource.GOOGLE_SEARCH)) {
                    if (retryCount < 1) {
                        retryCount++
                        scope.launch {
                            _isBuffering.value = true
                            _errorMessage.value = "Connecting to alternate stream..."
                            val freshUrl = youTubeMusicService.resolveAudioStreamUrl(
                                videoId = curr.sourceId,
                                title = curr.title,
                                artist = curr.artist,
                                forceFallback = true
                            )
                            if (!freshUrl.isNullOrEmpty() && freshUrl != curr.audioStreamUrl) {
                                val refreshedSong = curr.copy(audioStreamUrl = freshUrl)
                                _currentSong.value = refreshedSong
                                _errorMessage.value = null
                                playPreparedSong(refreshedSong, resumePosition = _currentPositionMs.value)
                            } else {
                                _isBuffering.value = false
                                _errorMessage.value = "Stream temporarily unavailable"
                                delay(3000)
                                _errorMessage.value = null
                            }
                        }
                    } else {
                        retryCount = 0
                        _isBuffering.value = false
                        _errorMessage.value = "Unable to play track: Check network connection"
                        scope.launch {
                            delay(3500)
                            _errorMessage.value = null
                        }
                    }
                } else {
                    _errorMessage.value = "Cannot play audio file"
                    scope.launch {
                        delay(3000)
                        _errorMessage.value = null
                    }
                }
            }
        })
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition
                _currentPositionMs.value = pos
                val dur = exoPlayer.duration
                if (dur > 0) {
                    _durationMs.value = dur
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        _currentPositionMs.value = exoPlayer.currentPosition
    }

    fun playSong(song: Song, newQueue: List<Song>? = null) {
        retryCount = 0
        if (newQueue != null && newQueue.isNotEmpty()) {
            originalQueue = newQueue.toMutableList()
            if (_shuffleEnabled.value) {
                val shuffled = newQueue.toMutableList()
                shuffled.remove(song)
                shuffled.shuffle()
                shuffled.add(0, song)
                _queue.value = shuffled
                _currentIndex.value = 0
            } else {
                _queue.value = newQueue
                _currentIndex.value = newQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            }
        } else {
            // Check if song exists in current queue
            val existingIdx = _queue.value.indexOfFirst { it.id == song.id }
            if (existingIdx != -1) {
                _currentIndex.value = existingIdx
            } else {
                val updated = _queue.value.toMutableList().apply { add(song) }
                _queue.value = updated
                _currentIndex.value = updated.size - 1
            }
        }

        _currentSong.value = song
        _currentPositionMs.value = 0L
        _durationMs.value = song.durationMs
        _errorMessage.value = null
        onSongPlayed?.invoke(song)

        scope.launch {
            resolveAndPlay(song)
        }
    }

    private suspend fun resolveAndPlay(song: Song, resumePosition: Long = 0L) {
        _isBuffering.value = true

        val streamUrl: String? = when (song.source) {
            SongSource.LOCAL -> song.audioStreamUrl ?: song.sourceId
            SongSource.YOUTUBE, SongSource.YOUTUBE_MUSIC, SongSource.GOOGLE_SEARCH -> {
                val candidate = song.audioStreamUrl
                if (!candidate.isNullOrEmpty() && candidate.contains(".r2.cloudflarestorage.com")) {
                    candidate
                } else if (!candidate.isNullOrEmpty()) {
                    youTubeMusicService.resolveDirectCdnUrl(candidate) ?: candidate
                } else {
                    youTubeMusicService.resolveAudioStreamUrl(
                        videoId = song.sourceId,
                        title = song.title,
                        artist = song.artist
                    )
                }
            }
        }

        if (streamUrl.isNullOrEmpty()) {
            _isBuffering.value = false
            _errorMessage.value = "Unable to extract audio stream"
            return
        }

        val updatedSong = song.copy(audioStreamUrl = streamUrl)
        _currentSong.value = updatedSong
        playPreparedSong(updatedSong, resumePosition)
    }

    private fun playPreparedSong(song: Song, resumePosition: Long = 0L) {
        val streamUrl = song.audioStreamUrl ?: return
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(streamUrl))
                .setMediaId(song.id)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            if (resumePosition > 0) {
                exoPlayer.seekTo(resumePosition)
            }
            exoPlayer.prepare()
            exoPlayer.play()

            notificationManager.updateNotification(song, true)
            MusicPlaybackService.startOrUpdateService(context, song, true)
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to start playback: ${e.message}", e)
            _errorMessage.value = "Failed to load audio"
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && _currentSong.value != null) {
            scope.launch {
                resolveAndPlay(_currentSong.value!!)
            }
        } else {
            exoPlayer.play()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _currentPositionMs.value = target
        exoPlayer.seekTo(target)
    }

    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = _currentIndex.value + 1
        if (nextIndex < q.size) {
            _currentIndex.value = nextIndex
            val nextSong = q[nextIndex]
            _currentSong.value = nextSong
            onSongPlayed?.invoke(nextSong)
            scope.launch { resolveAndPlay(nextSong) }
        } else if (_repeatMode.value == RepeatMode.ALL && q.isNotEmpty()) {
            _currentIndex.value = 0
            val nextSong = q[0]
            _currentSong.value = nextSong
            onSongPlayed?.invoke(nextSong)
            scope.launch { resolveAndPlay(nextSong) }
        } else {
            pause()
            seekTo(0)
        }
    }

    fun skipToPrevious() {
        // If passed 3 seconds, restart current track; otherwise go to previous
        if (_currentPositionMs.value > 3000L) {
            seekTo(0)
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return

        val prevIndex = _currentIndex.value - 1
        if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            val prevSong = q[prevIndex]
            _currentSong.value = prevSong
            onSongPlayed?.invoke(prevSong)
            scope.launch { resolveAndPlay(prevSong) }
        } else {
            seekTo(0)
        }
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                skipToNext()
            }
        }
    }

    fun toggleShuffle() {
        val current = !_shuffleEnabled.value
        _shuffleEnabled.value = current
        val currentSong = _currentSong.value

        if (current) {
            val shuffled = _queue.value.toMutableList()
            if (currentSong != null) {
                shuffled.remove(currentSong)
                shuffled.shuffle()
                shuffled.add(0, currentSong)
                _currentIndex.value = 0
            } else {
                shuffled.shuffle()
            }
            _queue.value = shuffled
        } else {
            // Restore original order
            if (originalQueue.isNotEmpty()) {
                _queue.value = originalQueue
                _currentIndex.value = originalQueue.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
            }
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        exoPlayer.volume = clamped
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
    }

    fun addToQueue(song: Song) {
        val list = _queue.value.toMutableList()
        list.add(song)
        _queue.value = list
        if (_currentSong.value == null) {
            playSong(song)
        }
    }

    fun playNext(song: Song) {
        val list = _queue.value.toMutableList()
        val insertIdx = (_currentIndex.value + 1).coerceIn(0, list.size)
        list.add(insertIdx, song)
        _queue.value = list
        if (_currentSong.value == null) {
            playSong(song)
        }
    }

    fun removeFromQueue(index: Int) {
        val list = _queue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _queue.value = list
            if (index < _currentIndex.value) {
                _currentIndex.value--
            } else if (index == _currentIndex.value) {
                if (list.isNotEmpty()) {
                    val nextIdx = index.coerceAtMost(list.size - 1)
                    _currentIndex.value = nextIdx
                    playSong(list[nextIdx])
                } else {
                    exoPlayer.stop()
                    _currentSong.value = null
                    _currentIndex.value = -1
                }
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list

            // Adjust currentIndex
            val curr = _currentIndex.value
            if (curr == fromIndex) {
                _currentIndex.value = toIndex
            } else if (fromIndex < curr && toIndex >= curr) {
                _currentIndex.value--
            } else if (fromIndex > curr && toIndex <= curr) {
                _currentIndex.value++
            }
        }
    }

    fun clearQueue() {
        val current = _currentSong.value
        if (current != null) {
            _queue.value = listOf(current)
            _currentIndex.value = 0
        } else {
            _queue.value = emptyList()
            _currentIndex.value = -1
        }
    }

    fun release() {
        progressJob?.cancel()
        notificationManager.cancelNotification()
        MusicPlaybackService.stopService(context)
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w("AudioPlayerManager", "MediaSession release: ${e.message}")
        }
        exoPlayer.release()
    }
}
