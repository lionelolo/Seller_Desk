package com.sellerdesk.app.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Loops the alarm ringtone on the ALARM audio stream (plays even in silent mode,
 * at max alarm volume) and vibrates until stop() is called. Safety cut-off: 10 min.
 */
object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    val isPlaying: Boolean @Synchronized get() = player != null

    @Synchronized
    fun start(context: Context) {
        if (player != null) return
        val app = context.applicationContext

        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player?.release()
            player = null
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400, 700, 400), 0))

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, 10 * 60_000L)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(autoStop)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }
}
