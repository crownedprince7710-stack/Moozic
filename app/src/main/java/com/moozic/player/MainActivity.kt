
package com.moozic.player

import android.Manifest
import android.content.ContentUris
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
            this, permission
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            MoozicApp(
                hasPermission = hasPermission,
                onRequestPermission = {
                    if (ContextCompat.checkSelfPermission(
                            this, permission
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

    val context = LocalContext.current
    val musicController = remember {
        MusicController(context)
    }

    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var loading by remember { mutableStateOf(false) }
    var scanVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasPermission, scanVersion) {
        if (hasPermission) {
            loading = true
            songs = onScanMusic()
            loading = false
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    "MOOZIC",
                    color = accent,
                    fontSize = 32.sp
                )

                Text(
                    "YOUR MUSIC. YOUR RULES.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(30.dp))

                Text(
                    "Your Library",
                    color = Color.White,
                    fontSize = 24.sp
                )

                Text(
                    "${songs.size} SONGS",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(20.dp))

                when {
                    !hasPermission -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text("SCAN MY MUSIC")
                            }
                        }
                    }

                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }

                    songs.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "No music found",
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            Text(
                                "Add audio files to your phone and scan again.",
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
                                Text("REFRESH LIBRARY")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(songs, key = { it.id }) { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Color(0xFF181818),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            val songUri =
                                                ContentUris.withAppendedId(
                                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                    song.id
                                                )

                                            musicController.play(
                                                songUri,
                                                song.title,
                                                song.artist
                                            )
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(
                                                Color(0xFF292020),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "♫",
                                            color = accent,
                                            fontSize = 25.sp
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            song.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            maxLines = 1
                                        )

                                        Text(
                                            song.artist,
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }

                                    Text(
                                        formatDuration(song.duration),
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
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
