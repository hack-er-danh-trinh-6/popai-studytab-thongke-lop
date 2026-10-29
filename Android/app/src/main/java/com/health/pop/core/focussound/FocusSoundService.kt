package com.health.pop.core.focussound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.health.pop.MainActivity

/**
 * Phát nhạc tập trung (stream từ backend) ở âm lượng 30% trong lúc đồng hồ tập trung chạy.
 * Lặp lại liên tục, tự nhường/tạm dừng khi app khác cần âm thanh và dừng khi rút tai nghe để không phát ra loa ngoài.
 */
class FocusSoundService : Service() {

    private lateinit var audioManager: AudioManager
    private var player: MediaPlayer? = null
    private var prepared = false
    private var pausedByFocusLoss = false
    private var focusRequest: AudioFocusRequest? = null
    private var errorShown = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopSelf()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                p.setVolume(FocusMusic.VOLUME, FocusMusic.VOLUME)
                if (pausedByFocusLoss && prepared) {
                    pausedByFocusLoss = false
                    p.start()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                p.setVolume(FocusMusic.DUCKED_VOLUME, FocusMusic.DUCKED_VOLUME)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (prepared && p.isPlaying) {
                    pausedByFocusLoss = true
                    p.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android bắt buộc gọi startForeground ngay sau startForegroundService, kể cả khi sau đó dừng.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!FocusSoundPrefs(this).enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) startPlayback()
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startPlayback() {
        if (!requestAudioFocus()) {
            stopSelf()
            return
        }
        try {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            p.setVolume(FocusMusic.VOLUME, FocusMusic.VOLUME)
            p.isLooping = true
            p.setOnPreparedListener {
                prepared = true
                if (!pausedByFocusLoss) it.start()
            }
            p.setOnErrorListener { _, _, _ ->
                onPlaybackError()
                true
            }
            p.setDataSource(FocusMusic.url())
            p.prepareAsync()
        } catch (_: Exception) {
            onPlaybackError()
        }
    }

    private fun onPlaybackError() {
        if (!errorShown) {
            errorShown = true
            Toast.makeText(this, "Không tải được nhạc tập trung. Kiểm tra kết nối mạng.", Toast.LENGTH_LONG).show()
        }
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun releasePlayer() {
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
        prepared = false
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: IllegalArgumentException) {
        }
        releasePlayer()
        abandonAudioFocus()
        super.onDestroy()
    }

    /* ───── Thông báo ───── */

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_RETURN_TO_FOCUS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Nhạc tập trung")
            .setContentText("Đang phát nhạc giúp bạn tập trung")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nhạc tập trung", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Hiện khi POP AI đang phát nhạc nền trong lúc đồng hồ tập trung chạy"
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "pop_focus_sound"
        private const val NOTIFICATION_ID = 7201

        /** Gọi khi người dùng vừa bấm bắt đầu/tiếp tục (app đang ở foreground). Bỏ qua nếu đã tắt trong cài đặt. */
        fun startIfEnabled(context: Context) {
            val app = context.applicationContext
            if (!FocusSoundPrefs(app).enabled) return
            try {
                ContextCompat.startForegroundService(app, Intent(app, FocusSoundService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, FocusSoundService::class.java))
        }
    }
}
