package com.example.dadamusicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private var currentTitle: String = "Dada Music Player"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "DadaMusicService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val title = intent?.getStringExtra("songTitle") ?: currentTitle
        currentTitle = title
        val songUriString = intent?.getStringExtra("songUri")

        when (action) {
            "PLAY_URI" -> {
                songUriString?.let { uriStr ->
                    playAudioUri(Uri.parse(uriStr))
                }
            }
            "PAUSE" -> {
                mediaPlayer?.pause()
            }
            "RESUME" -> {
                mediaPlayer?.start()
            }
            "STOP" -> {
                // Music aur Service dono ko completely close/cross karne ke liye
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        showNotification(currentTitle, mediaPlayer?.isPlaying ?: false)
        return START_STICKY
    }

    private fun playAudioUri(uri: Uri) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, uri)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotification(title: String, isPlaying: Boolean) {
        // App open karne ke liye intent
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification close (Cross) karne ke liye intent
        val stopIntent = Intent(this, MusicService::class.java).apply {
            action = "STOP"
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle(title)
            .setContentText("Dada Music Playing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            // Cross / Close button Notification bar me add ho gaya hai
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close ❌", pendingStopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CHANNEL_ID",
                "Dada Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
