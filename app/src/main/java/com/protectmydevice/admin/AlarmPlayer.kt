package com.protectmydevice.admin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager

/** Plays a loud alarm at max volume, even if the phone is on silent. */
object AlarmPlayer {
    private var player: MediaPlayer? = null

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
    }

    fun stop() {
        player?.run { stop(); release() }
        player = null
    }
}
