package com.example.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.data.model.Song

class MusicPlaybackService : Service() {

    companion object {
        var playerManager: AudioPlayerManager? = null
        var notificationManager: MusicNotificationManager? = null

        fun startOrUpdateService(context: Context, song: Song?, isPlaying: Boolean) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = if (isPlaying) "ACTION_START_FOREGROUND" else "ACTION_UPDATE"
            }
            try {
                if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("MusicPlaybackService", "Failed to start service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MusicPlaybackService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w("MusicPlaybackService", "Failed to stop service: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val manager = playerManager
        val notifManager = notificationManager

        when (action) {
            MusicNotificationManager.ACTION_TOGGLE_PLAY -> {
                manager?.togglePlayPause()
            }
            MusicNotificationManager.ACTION_PLAY -> {
                manager?.resume()
            }
            MusicNotificationManager.ACTION_PAUSE -> {
                manager?.pause()
            }
            MusicNotificationManager.ACTION_NEXT -> {
                manager?.skipToNext()
            }
            MusicNotificationManager.ACTION_PREV -> {
                manager?.skipToPrevious()
            }
            MusicNotificationManager.ACTION_STOP -> {
                manager?.pause()
                notifManager?.cancelNotification()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            "ACTION_START_FOREGROUND" -> {
                val currentSong = manager?.currentSong?.value
                val isPlaying = manager?.isPlaying?.value ?: false
                if (notifManager != null && currentSong != null) {
                    val notif = notifManager.buildNotification(currentSong, isPlaying)
                    startForegroundWithCompat(notif)
                }
            }
            "ACTION_UPDATE" -> {
                val currentSong = manager?.currentSong?.value
                val isPlaying = manager?.isPlaying?.value ?: false
                if (notifManager != null && currentSong != null) {
                    notifManager.updateNotification(currentSong, isPlaying)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithCompat(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    MusicNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(MusicNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "Error starting foreground: ${e.message}")
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "Error stopping foreground: ${e.message}")
        }
    }

    override fun onDestroy() {
        notificationManager?.cancelNotification()
        super.onDestroy()
    }
}
