
package com.moozic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestAudioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadMusic = true
            }
        }

    private var loadMusic by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoozicApp(
                hasPermission = loadMusic,
                onRequestPermission = {
                    val permission = if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                    if (ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        loadMusic = true
                    } else {
                        requestAudioPermission.launch(permission)
                    }
                }
            )
        }
    }
}

@Composable
fun MoozicApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val background = Color(0xFF090909)
    val accent = Color(0xFFFF3434)

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

                Spacer(Modifier.height(16.dp))

                if (!hasPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Your music starts here.",
                                color = Color.White
                            )

                            Spacer(Modifier.height(12.dp))

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
                } else {
                    val songs = remember {
                        mutableStateListOf<String>()
                    }

                    LaunchedEffect(Unit) {
                        // Local music scanning will be connected next.
                    }

                    if (songs.isEmpty()) {
                        Text(
                            "Music library is ready.",
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn {
                            items(songs) { song ->
                                Text(
                                    song,
                                    color = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
