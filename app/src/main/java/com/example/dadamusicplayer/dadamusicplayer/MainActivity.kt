package com.example.dadamusicplayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

class MusicService : Service() {

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    var currentSongTitle: String = "No track loaded"
        private set
    var isPlaying: Boolean = false
        private set

    companion object {
        const val CHANNEL_ID = "dada_music_channel"
        const val NOTIF_ID = 101
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    fun playRandomSong(context: Context) {
        val songList = fetchAudioFiles(context)
        if (songList.isEmpty()) {
            currentSongTitle = "No Audio Files Found"
            return
        }

        val randomIndex = Random.nextInt(songList.size)
        val selectedSong = songList[randomIndex]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, selectedSong.uri)
            prepare()
            start()
        }

        currentSongTitle = selectedSong.title
        isPlaying = true
        startForeground(NOTIF_ID, createNotification())
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
            startForeground(NOTIF_ID, createNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dada Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dada Music Player")
            .setContentText(currentSongTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(mainPendingIntent)
            .addAction(playIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun fetchAudioFiles(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songs.add(Song(contentUri, title))
            }
        }
        return songs
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}

data class Song(val uri: Uri, val title: String)

class MainActivity : ComponentActivity() {

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        checkAndRequestPermissions()

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerUI(
                        onPlayRandomClicked = {
                            if (isBound) musicService?.playRandomSong(this)
                        },
                        onTogglePlayPauseClicked = {
                            if (isBound) musicService?.togglePlayPause()
                        },
                        getSongTitle = { musicService?.currentSongTitle ?: "No Song Selected" },
                        getIsPlaying = { musicService?.isPlaying ?: false }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun PlayerUI(
    onPlayRandomClicked: () -> Unit,
    onTogglePlayPauseClicked: () -> Unit,
    getSongTitle: () -> String,
    getIsPlaying: () -> Boolean
) {
    var songTitle by remember { mutableStateOf("Press Play to Start") }
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Dada Music Player", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Made by Pratham Dada (IGNOU BCA Student)", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Now Playing", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = songTitle, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    onPlayRandomClicked()
                    songTitle = getSongTitle()
                    isPlaying = getIsPlaying()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Play Random Song", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onTogglePlayPauseClicked()
                    isPlaying = getIsPlaying()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = if (isPlaying) "Pause" else "Play / Resume", fontSize = 16.sp)
            }
        }
    }
}
