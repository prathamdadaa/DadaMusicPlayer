package com.example.dadamusicplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val contentUri: Uri? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var isSplashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000)
        isSplashVisible = false
    }

    Crossfade(
        targetState = isSplashVisible,
        animationSpec = tween(durationMillis = 500),
        label = "SplashToMain"
    ) { showSplash ->
        if (showSplash) {
            SplashScreen()
        } else {
            MusicPlayerScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🎙️", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DADA MUSIC PLAYER",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {
    val context = LocalContext.current

    val defaultPlaylist = listOf(
        Song("295 (Demo)", "Sidhu Moose Wala", "4:30"),
        Song("The Last Ride (Demo)", "Sidhu Moose Wala", "4:22"),
        Song("So High (Demo)", "Sidhu Moose Wala", "3:37")
    )

    var songList by remember { mutableStateOf(defaultPlaylist) }
    var currentSongIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val localSongs = fetchAudioFiles(context)
            if (localSongs.isNotEmpty()) {
                songList = localSongs
                currentSongIndex = 0
            }
        }
    }

    val currentSong = songList.getOrElse(currentSongIndex) { defaultPlaylist[0] }

    fun sendActionToService(action: String, uri: Uri? = null) {
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
            putExtra("songTitle", currentSong.title)
            putExtra("isPlaying", isPlaying)
            uri?.let { putExtra("songUri", it.toString()) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎵 Dada Music Player", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎶", fontSize = 60.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentSong.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong.artist,
                fontSize = 14.sp,
                color = Color.Gray
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentSongIndex > 0) currentSongIndex-- else currentSongIndex = songList.size - 1
                    currentSong.contentUri?.let { uri ->
                        isPlaying = true
                        sendActionToService("PLAY_URI", uri)
                    }
                }) {
                    Text(text = "⏮️", fontSize = 28.sp)
                }

                Button(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            currentSong.contentUri?.let { uri ->
                                sendActionToService("PLAY_URI", uri)
                            } ?: run { sendActionToService("RESUME") }
                        } else {
                            sendActionToService("PAUSE")
                        }
                    },
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape
                ) {
                    Text(text = if (isPlaying) "⏸" else "▶", fontSize = 22.sp)
                }

                IconButton(onClick = {
                    if (currentSongIndex < songList.size - 1) currentSongIndex++ else currentSongIndex = 0
                    currentSong.contentUri?.let { uri ->
                        isPlaying = true
                        sendActionToService("PLAY_URI", uri)
                    }
                }) {
                    Text(text = "⏭️", fontSize = 28.sp)
                }
            }

            Button(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        permissionToRequest
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        val localSongs = fetchAudioFiles(context)
                        if (localSongs.isNotEmpty()) {
                            songList = localSongs
                            currentSongIndex = 0
                        }
                    } else {
                        launcher.launch(permissionToRequest)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(text = "📁 Load Phone's Local MP3 Songs")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(songList) { index, song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                currentSongIndex = index
                                isPlaying = true
                                song.contentUri?.let { uri ->
                                    sendActionToService("PLAY_URI", uri)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == currentSongIndex)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}. ${song.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = song.duration,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun fetchAudioFiles(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Unknown Track"
            val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
            val durationMs = cursor.getLong(durationColumn)

            val minutes = (durationMs / 1000) / 60
            val seconds = (durationMs / 1000) % 60
            val formattedDuration = String.format("%d:%02d", minutes, seconds)

            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            songs.add(Song(title, artist, formattedDuration, contentUri))
        }
    }
    return songs
}
