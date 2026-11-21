
package com.moozic.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

class MusicController(context: Context) {

    private val appContext = context.applicationContext

    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicService::class.java)
    )

    private val controllerFuture =
        MediaController.Builder(appContext, sessionToken)
            .buildAsync()

    fun play(
        uri: Uri,
        title: String,
        artist: String
    ) {
        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get()

                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .build()
                        )
                        .build()

                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            Executor { command -> command.run() }
        )
    }
}
