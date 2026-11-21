
package com.moozic.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

class MusicController(context: Context) {

    private val appContext = context.applicationContext

    private val controllerFuture =
        MediaController.Builder(
            appContext,
            SessionToken(
                appContext,
                ComponentName(appContext, MusicService::class.java)
            )
        ).buildAsync()

    private val directExecutor = Executor { it.run() }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()

                val mediaItems = songs.map { song ->
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        song.id.toString()
                    )

                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .build()
                        )
                        .build()
                }

                controller.setMediaItems(mediaItems, startIndex, 0L)
                controller.prepare()
                controller.play()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, directExecutor)
    }

    fun pause() {
        controllerFuture.addListener({
            try {
                controllerFuture.get().pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, directExecutor)
    }

    fun resume() {
        controllerFuture.addListener({
            try {
                controllerFuture.get().play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, directExecutor)
    }

    fun next() {
        controllerFuture.addListener({
            try {
                controllerFuture.get().seekToNextMediaItem()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, directExecutor)
    }

    fun previous() {
        controllerFuture.addListener({
            try {
                controllerFuture.get().seekToPreviousMediaItem()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, directExecutor)
    }
}
