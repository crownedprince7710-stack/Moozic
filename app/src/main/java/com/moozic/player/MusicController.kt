
package com.moozic.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MusicController(context: Context) {

    private val appContext = context.applicationContext

    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicService::class.java)
    )

    private val controllerFuture =
        MediaController.Builder(
            appContext,
            sessionToken
        ).buildAsync()

    private val mainExecutor =
        ContextCompat.getMainExecutor(appContext)

    val queue = mutableStateListOf<Song>()

    var isConnected by mutableStateOf(false)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentIndex by mutableStateOf(-1)
        private set

    var currentTitle by mutableStateOf("Nothing playing")
        private set

    var currentArtist by mutableStateOf("")
        private set

    var currentPosition by mutableStateOf(0L)
        private set

    var duration by mutableStateOf(0L)
        private set

    var playbackState by mutableStateOf(Player.STATE_IDLE)
        private set

    var shuffleEnabled by mutableStateOf(false)
        private set

    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set

    var volume by mutableStateOf(1f)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            playbackState = state
            updatePlaybackInfo()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            updatePlaybackInfo()
        }

        override fun onMediaMetadataChanged(
            mediaMetadata: MediaMetadata
        ) {
            updatePlaybackInfo()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlaybackInfo()
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            shuffleEnabled = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeatMode = mode
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = error.message ?: "Unable to play this song"
            isPlaying = false
        }
    }

    init {
        controllerFuture.addListener(
            {
                try {
                    val connectedController = controllerFuture.get()

                    controller = connectedController
                    connectedController.addListener(playerListener)

                    isConnected = true
                    updatePlaybackInfo()

                } catch (e: Exception) {
                    errorMessage =
                        e.cause?.message ?: e.message
                        ?: "Unable to connect to music service"
                }
            },
            mainExecutor
        )
    }

    private fun withController(
        action: (MediaController) -> Unit
    ) {
        controllerFuture.addListener(
            {
                try {
                    controller?.let { action(it) }
                } catch (e: Exception) {
                    errorMessage =
                        e.message ?: "Playback operation failed"
                }
            },
            mainExecutor
        )
    }

    private fun createMediaItem(song: Song): MediaItem {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            song.id
        )

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .build()
            )
            .build()
    }

    fun playQueue(
        songs: List<Song>,
        startIndex: Int
    ) {
        if (songs.isEmpty()) return
        if (startIndex !in songs.indices) return

        errorMessage = null

        withController { player ->

            queue.clear()
            queue.addAll(songs)

            val mediaItems = songs.map {
                createMediaItem(it)
            }

            player.setMediaItems(
                mediaItems,
                startIndex,
                0L
            )

            player.prepare()
            player.play()

            updatePlaybackInfo()
        }
    }

    fun playNext(song: Song) {
        errorMessage = null

        withController { player ->

            val insertIndex =
                if (player.mediaItemCount == 0) {
                    0
                } else {
                    (player.currentMediaItemIndex + 1)
                        .coerceIn(0, player.mediaItemCount)
                }

            player.addMediaItem(
                insertIndex,
                createMediaItem(song)
            )

            val queueInsertIndex =
                insertIndex.coerceIn(0, queue.size)

            queue.add(queueInsertIndex, song)

            if (player.mediaItemCount == 1) {
                player.prepare()
                player.play()
            }
        }
    }

    fun play() {
        errorMessage = null

        withController {
            it.play()
        }
    }

    fun pause() {
        withController {
            it.pause()
        }
    }

    fun togglePlayPause() {
        withController { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun next() {
        withController { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }
    }

    fun previous() {
        withController { player ->
            if (player.currentPosition > 3000L) {
                player.seekTo(0L)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }
    }

    fun seekTo(position: Long) {
        withController { player ->
            player.seekTo(position.coerceAtLeast(0L))
        }
    }

    fun setVolume(value: Float) {
        val safeVolume = value.coerceIn(0f, 1f)

        volume = safeVolume

        withController {
            it.volume = safeVolume
        }
    }

    fun toggleShuffle() {
        withController { player ->
            player.shuffleModeEnabled =
                !player.shuffleModeEnabled
        }
    }

    fun toggleRepeat() {
        withController { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF ->
                    Player.REPEAT_MODE_ALL

                Player.REPEAT_MODE_ALL ->
                    Player.REPEAT_MODE_ONE

                else ->
                    Player.REPEAT_MODE_OFF
            }
        }
    }

    fun updatePlaybackInfo() {
        val player = controller ?: return

        isPlaying = player.isPlaying
        playbackState = player.playbackState

        currentIndex = player.currentMediaItemIndex

        currentPosition = player.currentPosition.coerceAtLeast(0L)

        duration = if (player.duration > 0L) {
            player.duration
        } else {
            0L
        }

        shuffleEnabled = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        volume = player.volume

        player.currentMediaItem?.mediaMetadata?.let {
            currentTitle = it.title?.toString()
                ?: "Unknown title"

            currentArtist = it.artist?.toString()
                ?: "Unknown artist"
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
