package com.roundsalmon4.phonetube.player.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.roundsalmon4.phonetube.MainActivity
import com.roundsalmon4.phonetube.player.PlayerEngineController

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            FOREGROUND_SERVICE_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = playerController?.exoPlayer ?: return START_NOT_STICKY
        if (mediaSession == null) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build()
        }
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun createNotification(): android.app.Notification {
        val channel = android.app.NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Playback",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playing video"
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PhoneTube")
            .setContentText("Playing video")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val FOREGROUND_SERVICE_ID = 1

        @Volatile
        var playerController: PlayerEngineController? = null

        fun start(controller: PlayerEngineController, context: android.content.Context) {
            playerController = controller
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, PlaybackService::class.java)
            context.stopService(intent)
            playerController = null
        }
    }
}
