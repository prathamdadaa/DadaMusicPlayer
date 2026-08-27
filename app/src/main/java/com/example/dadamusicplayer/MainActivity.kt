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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// Theme Colors (Warm Soft Sand/Beige Theme)
val VintageBackground = Color(0xFFD6C6A5)
val SoftCardBg = Color(0xFFE4D5B7)
val SoftInnerShadow = Color(0xFFC7B693)
val DarkText = Color(0xFF4A3E2D)
val MutedText = Color(0xFF7A6B56)

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
                    color = VintageBackground
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

// 1. Black Splash Screen (Unchanged)
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

// 2. Vintage Soft Gold Neumorphic Music Player
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Header Title
        Text(
            text = "DADA MUSIC PLAYER",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main Neumorphic Card Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(32.dp), spotColor = DarkText),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = SoftCardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular Album Artwork Frame
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(SoftInnerShadow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎼", fontSize = 70.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song Title & Artist
                Text(
                    text = currentSong.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong.artist,
                    fontSize = 14.sp,
                    color = MutedText
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Control Buttons Row (Neumorphic Soft Buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    IconButton(
                        onClick = {
                            if (currentSongIndex > 0) currentSongIndex-- else currentSongIndex = songList.size - 1
                            currentSong.contentUri?.let { uri ->
                                isPlaying = true
                                sendActionToService("PLAY_URI", uri)
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(6.dp, CircleShape)
                            .background(SoftCardBg, CircleShape)
                    ) {
                        Text(text = "⏮", fontSize = 20.sp, color = DarkText)
                    }

                    // Play/Pause Center Button
                    IconButton(
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
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(8.dp, CircleShape)
                            .background(SoftCardBg, CircleShape)
                    ) {
                        Text(text = if (isPlaying) "⏸" else "▶", fontSize = 26.sp, color = DarkText)
                    }

                    // Next Button
                    IconButton(
                        onClick = {
                            if (currentSongIndex < songList.size - 1) currentSongIndex++ else currentSongIndex = 0
                            currentSong.contentUri?.let { uri ->
                                isPlaying = true
                                sendActionToService("PLAY_URI", uri)
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(6.dp, CircleShape)
                            .background(SoftCardBg, CircleShape)
                    ) {
                        Text(text = "⏭", fontSize = 20.sp, color = DarkText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Load Phone Songs Button
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
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SoftCardBg)
        ) {
            Text(text = "📁 Load Phone's Local Songs", color = DarkText, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Song List
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == currentSongIndex) SoftInnerShadow else SoftCardBg
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${song.title}",
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                fontSize = 12.sp,
                                color = MutedText
                            )
                        }
                        Text(
                            text = song.duration,
                            fontSize = 12.sp,
                            color = MutedText
                        )
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
