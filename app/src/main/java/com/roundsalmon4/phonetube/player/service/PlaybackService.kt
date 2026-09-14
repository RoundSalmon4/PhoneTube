package com.roundsalmon4.phonetube.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.roundsalmon4.phonetube.MainActivity
import com.roundsalmon4.phonetube.player.PlayerEngineController

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            refreshNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Guarantee the foreground is started in time (startForegroundService 5s rule).
        startForeground(
            NOTIFICATION_ID,
            buildPlaceholderNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = playerController?.exoPlayer ?: return START_NOT_STICKY

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                player.playWhenReady = !player.playWhenReady
            }
            ACTION_PREVIOUS -> {
                // Replicate common player behavior: if more than a few seconds in,
                // restart the current video; otherwise go to the previous item.
                if (player.currentPosition > 3000L && player.playbackState == Player.STATE_READY) {
                    player.seekTo(0)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                }
            }
            ACTION_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                } else {
                    player.stop()
                    player.clearMediaItems()
                    stopSelf()
                }
            }
        }

        if (mediaSession == null) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build()
            player.addListener(playerListener)
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(player),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        return START_NOT_STICKY
    }

    private fun refreshNotification() {
        val player = playerController?.exoPlayer ?: return
        startForeground(
            NOTIFICATION_ID,
            buildNotification(player),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop playback when the app is swiped away from recents
        val player = mediaSession?.player
        if (player != null) {
            player.stop()
            player.clearMediaItems()
        }
        playerController = null
        stopSelf()
    }

    override fun onDestroy() {
        playerController?.exoPlayer?.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun buildNotification(player: Player): Notification {
        val isPlaying = player.isPlaying
        val title = player.mediaMetadata.title?.toString() ?: "PhoneTube"
        val artist = player.mediaMetadata.artist?.toString() ?: "Playing video"

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS)
        val previousPending = PendingIntent.getService(
            this, 2, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT)
        val nextPending = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY_PAUSE)
        val playPausePending = PendingIntent.getService(
            this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPausePending
        ).build()

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", previousPending).build())
            .addAction(playPauseAction)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", nextPending).build())
            .build()
    }

    private fun buildPlaceholderNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PhoneTube")
            .setContentText("Starting playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playing video"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY_PAUSE = "com.roundsalmon4.phonetube.action.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.roundsalmon4.phonetube.action.PREVIOUS"
        private const val ACTION_NEXT = "com.roundsalmon4.phonetube.action.NEXT"

        @Volatile
        var playerController: PlayerEngineController? = null

        fun start(controller: PlayerEngineController, context: Context) {
            playerController = controller
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            context.stopService(intent)
            playerController = null
        }
    }
}
