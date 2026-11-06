package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "aura_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.auramusic.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.auramusic.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.example.auramusic.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.auramusic.ACTION_NEXT"
        const val ACTION_PREV = "com.example.auramusic.ACTION_PREV"
        const val ACTION_STOP = "com.example.auramusic.ACTION_STOP"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastArtworkUrl: String? = null
    private var cachedArtworkBitmap: Bitmap? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        song: Song?,
        isPlaying: Boolean,
        artworkBitmap: Bitmap? = cachedArtworkBitmap
    ): Notification {
        val title = song?.title ?: "Aura Music"
        val artist = song?.artist ?: "Ready to stream"
        val album = song?.album ?: "Online Player"

        // Intent to launch MainActivity when clicking the notification
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action Intent
        val prevIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            context,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause Action Intent
        val playPauseIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next Action Intent
        val nextIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            context,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action Intent (when dismissed)
        val stopIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(R.drawable.ic_notif_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_notif_pause, "Close", stopPendingIntent)

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap)
        }

        return builder.build()
    }

    fun updateNotification(song: Song?, isPlaying: Boolean) {
        if (song == null) {
            cancelNotification()
            return
        }

        val artworkUrl = song.artworkUrl

        // If artwork URL changed, fetch the new bitmap asynchronously
        if (!artworkUrl.isNullOrEmpty() && artworkUrl != lastArtworkUrl) {
            lastArtworkUrl = artworkUrl
            scope.launch {
                val bitmap = loadBitmapFromUrl(artworkUrl)
                cachedArtworkBitmap = bitmap
                val notification = buildNotification(song, isPlaying, bitmap)
                try {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.w("MusicNotification", "Error updating notification with bitmap: ${e.message}")
                }
            }
        } else {
            val notification = buildNotification(song, isPlaying, cachedArtworkBitmap)
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w("MusicNotification", "Error updating notification: ${e.message}")
            }
        }
    }

    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w("MusicNotification", "Error cancelling notification: ${e.message}")
        }
    }

    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Software bitmap required for Notification largeIcon
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("MusicNotification", "Failed to load artwork for notification: ${e.message}")
            null
        }
    }
}
