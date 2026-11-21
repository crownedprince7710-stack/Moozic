
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
    val hasCurrentSong = currentIndex in songs.indices

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

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Your Library",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "${songs.size} SONGS",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    if (hasPermission) {
                        TextButton(
                            onClick = { scanVersion++ }
                        ) {
                            Text(
                                "REFRESH",
                                color = accent
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                when {
                    !hasPermission -> {
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

                    loading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }

                    scanError != null -> {
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

                    songs.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No music found",
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Add audio files to your phone and scan again.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { scanVersion++ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text("SCAN AGAIN")
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

                                val selected = currentIndex == index

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selected) Color(0xFF302020)
                                            else cardColor,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            musicController.playQueue(
                                                songs,
                                                index
                                            )
                                        }
                                        .padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                Color(0xFF292020),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (selected && isPlaying) "♫"
                                            else "♪",
                                            color = accent,
                                            fontSize = 24.sp
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = song.title,
                                            color = if (selected) accent
                                            else Color.White,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = song.artist,
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = formatDuration(song.duration),
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
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
                            .padding(15.dp)
                    ) {

                        Text(
                            text = "NOW PLAYING",
                            color = accent,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = musicController.currentTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = musicController.currentArtist,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(12.dp))

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
                                    fontSize = 11.sp
                                )
                            }

                            TextButton(
                                onClick = {
                                    musicController.previous()
                                }
                            ) {
                                Text(
                                    "PREV",
                                    color = Color.White
                                )
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
                                Text(
                                    "NEXT",
                                    color = Color.White
                                )
                            }

                            TextButton(
                                onClick = {
                                    musicController.toggleRepeat()
                                }
                            ) {
                                val repeatText = when (
                                    musicController.repeatMode
                                ) {
                                    Player.REPEAT_MODE_ALL -> "REPEAT ALL"
                                    Player.REPEAT_MODE_ONE -> "REPEAT ONE"
                                    else -> "REPEAT OFF"
                                }

                                Text(
                                    repeatText,
                                    color = if (
                                        musicController.repeatMode !=
                                        Player.REPEAT_MODE_OFF
                                    ) accent else Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (musicController.errorMessage != null) {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = musicController.errorMessage
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
