package com.protectmydevice.admin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import com.protectmydevice.battery.BatteryCollector
import com.protectmydevice.battery.Key

/** Plays a loud alarm at max volume, even if the phone is on silent. */
object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var startedAt = 0L
    private var appContext: Context? = null

    fun start(context: Context) {
        if (player != null) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }
        startedAt = System.currentTimeMillis()
        appContext = context.applicationContext
    }

    fun stop() {
        player?.run { stop(); release() }
        // Battery report: how long it sounded. Lost if the app is killed mid-alarm; the audio timer still counts it.
        if (player != null) appContext?.let { ctx -> runCatching { BatteryCollector.noteSeconds(ctx, Key.ALARM_S, startedAt) } }
        player = null
    }
}
