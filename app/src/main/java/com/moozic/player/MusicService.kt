
package com.moozic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .build()
            .apply {

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )

                setHandleAudioBecomingNoisy(true)

                setWakeMode(C.WAKE_MODE_LOCAL)
            }

        player = exoPlayer

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        val sessionActivity = launchIntent?.let { intent ->
            PendingIntent.getActivity(
                this,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        val sessionBuilder =
            MediaSession.Builder(this, exoPlayer)

        if (sessionActivity != null) {
            sessionBuilder.setSessionActivity(sessionActivity)
        }

        mediaSession = sessionBuilder.build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player

        if (currentPlayer == null || !currentPlayer.isPlaying) {
            stopSelf()
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
    }
}
