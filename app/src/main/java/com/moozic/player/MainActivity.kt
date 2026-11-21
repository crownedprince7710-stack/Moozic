
package com.moozic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        hasPermission = ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            MoozicApp(
                hasPermission = hasPermission,
                onRequestPermission = {
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        hasPermission = true
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                onScanMusic = { scanMusic() }
            )
        }
    }

    private suspend fun scanMusic(): List<Song> =
        withContext(Dispatchers.IO) {

            val songs = mutableListOf<Song>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )

            val selection =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            val sortOrder =
                "${MediaStore.Audio.Media.TITLE} ASC"

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )

                val titleColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )

                val artistColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )

                val durationColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION
                )

                while (cursor.moveToNext()) {
                    songs.add(
                        Song(
                            id = cursor.getLong(idColumn),
                            title = cursor.getString(titleColumn)
                                ?: "Unknown title",
                            artist = cursor.getString(artistColumn)
                                ?: "Unknown artist",
                            duration = cursor.getLong(durationColumn)
                        )
                    )
                }
            }

            songs
        }
}

@Composable
fun MoozicApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScanMusic: suspend () -> List<Song>
) {
    val background = Color(0xFF090909)
    val accent = Color(0xFFFF3434)
    val cardColor = Color(0xFF181818)

    val context = LocalContext.current

    val musicController = remember {
        MusicController(context)
    }

    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var draggingProgress by remember {
        mutableStateOf(false)
    }

    var sliderPosition by remember {
        mutableFloatStateOf(0f)
    }

    var showingQueue by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(musicController) {
        while (true) {
            musicController.updatePlaybackInfo()
            delay(500)
        }
    }

    LaunchedEffect(hasPermission, scanVersion) {
        if (hasPermission) {
            loading = true
            scanError = null

            try {
                songs = onScanMusic()
            } catch (e: Exception) {
                scanError = e.message ?: "Unable to scan music files"
            } finally {
                loading = false
            }
        }
    }

    val currentIndex = musicController.currentIndex
    val isPlaying = musicController.isPlaying
    val queue = musicController.queue
    val hasCurrentSong = currentIndex in queue.indices

    val duration = musicController.duration
    val currentPosition = musicController.currentPosition

    val progress = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {

                Text(
                    text = "MOOZIC",
                    color = accent,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "YOUR MUSIC. YOUR RULES.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (showingQueue) "Playing Queue"
                            else "Your Library",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = if (showingQueue) "${queue.size} TRACKS"
                            else "${songs.size} SONGS",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    if (!showingQueue && hasPermission) {
                        TextButton(
                            onClick = { scanVersion++ }
                        ) {
                            Text("REFRESH", color = accent)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            cardColor,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (!showingQueue) accent
                                else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                showingQueue = false
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "LIBRARY",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (showingQueue) accent
                                else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                showingQueue = true
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "QUEUE (${queue.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                when {
                    !showingQueue && !hasPermission -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text("ALLOW MUSIC ACCESS")
                            }
                        }
                    }

                    !showingQueue && loading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }

                    !showingQueue && scanError != null -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = scanError ?: "Something went wrong",
                                color = Color.White
                            )

                            Button(
                                onClick = { scanVersion++ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text("TRY AGAIN")
                            }
                        }
                    }

                    !showingQueue && songs.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No music found. Add audio files and scan again.",
                                color = Color.Gray
                            )
                        }
                    }

                    showingQueue && queue.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Your queue is empty",
                                    color = Color.White,
                                    fontSize = 18.sp
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "Add songs from your library.",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )

                                TextButton(
                                    onClick = { showingQueue = false }
                                ) {
                                    Text("BROWSE LIBRARY", color = accent)
                                }
                            }
                        }
                    }

                    showingQueue -> {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "UP NEXT",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    onClick = {
                                        musicController.clearQueue()
                                    }
                                ) {
                                    Text(
                                        "CLEAR QUEUE",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    queue.toList(),
                                    key = { index, song ->
                                        "${song.id}_$index"
                                    }
                                ) { index, song ->

                                    val isCurrent = index == currentIndex

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isCurrent) Color(0xFF302020)
                                                else cardColor,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                musicController.playAt(index)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isCurrent) "♫" else "${index + 1}",
                                            color = accent,
                                            fontSize = 16.sp,
                                            modifier = Modifier.width(30.dp)
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                song.title,
                                                color = if (isCurrent) accent
                                                else Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Text(
                                                song.artist,
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row {
                                                TextButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            musicController.moveQueueItem(
                                                                index,
                                                                index - 1
                                                            )
                                                        }
                                                    },
                                                    enabled = index > 0,
                                                    contentPadding = PaddingValues(4.dp)
                                                ) {
                                                    Text("↑", color = Color.White)
                                                }

                                                TextButton(
                                                    onClick = {
                                                        if (index < queue.lastIndex) {
                                                            musicController.moveQueueItem(
                                                                index,
                                                                index + 1
                                                            )
                                                        }
                                                    },
                                                    enabled = index < queue.lastIndex,
                                                    contentPadding = PaddingValues(4.dp)
                                                ) {
                                                    Text("↓", color = Color.White)
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    musicController.removeFromQueue(index)
                                                },
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(
                                                    "REMOVE",
                                                    color = accent,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            itemsIndexed(
                                songs,
                                key = { _, song -> song.id }
                            ) { index, song ->

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardColor, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                musicController.playQueue(
                                                    songs,
                                                    index
                                                )
                                            }
                                    ) {
                                        Text(
                                            song.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            song.artist,
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            musicController.playNext(song)
                                        }
                                    ) {
                                        Text(
                                            "PLAY NEXT",
                                            color = accent,
                                            fontSize = 10.sp
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            musicController.addToQueue(song)
                                        }
                                    ) {
                                        Text(
                                            "+ ADD",
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasCurrentSong) {
                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF202020),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            "NOW PLAYING",
                            color = accent,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            musicController.currentTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            musicController.currentArtist,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = if (draggingProgress) sliderPosition else progress,
                            onValueChange = {
                                draggingProgress = true
                                sliderPosition = it
                            },
                            onValueChangeFinished = {
                                musicController.seekTo(
                                    (sliderPosition * duration).toLong()
                                )
                                draggingProgress = false
                            },
                            enabled = duration > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatDuration(
                                    if (draggingProgress) {
                                        (sliderPosition * duration).toLong()
                                    } else {
                                        currentPosition
                                    }
                                ),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )

                            Text(
                                formatDuration(duration),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    musicController.toggleShuffle()
                                }
                            ) {
                                Text(
                                    "SHUFFLE",
                                    color = if (musicController.shuffleEnabled)
                                        accent else Color.Gray,
                                    fontSize = 10.sp
                                )
                            }

                            TextButton(
                                onClick = {
                                    musicController.previous()
                                }
                            ) {
                                Text("PREV", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    musicController.togglePlayPause()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text(
                                    if (isPlaying) "PAUSE" else "PLAY",
                                    color = Color.White
                                )
                            }

                            TextButton(
                                onClick = {
                                    musicController.next()
                                }
                            ) {
                                Text("NEXT", color = Color.White)
                            }

                            TextButton(
                                onClick = {
                                    musicController.toggleRepeat()
                                }
                            ) {
                                val repeatText = when (
                                    musicController.repeatMode
                                ) {
                                    Player.REPEAT_MODE_ALL -> "ALL"
                                    Player.REPEAT_MODE_ONE -> "ONE"
                                    else -> "OFF"
                                }

                                Text(
                                    "REPEAT $repeatText",
                                    color = if (
                                        musicController.repeatMode !=
                                        Player.REPEAT_MODE_OFF
                                    ) accent else Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (musicController.errorMessage != null) {
                            Text(
                                musicController.errorMessage
                                    ?: "Playback error",
                                color = accent,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(duration: Long): String {
    val totalSeconds = duration / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}
