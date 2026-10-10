package com.dhruv.focusguard.guard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Stops Instagram's reel audio from playing under the pause screen.
 *
 * Two layers, because neither is guaranteed on its own:
 *  1. Take transient audio focus: well-behaved players (Instagram included)
 *     pause, and resume when we hand focus back after "Continue".
 *  2. Mute the media stream for as long as the pause is up. This catches the
 *     first few hundred ms before the overlay exists and any player that
 *     ignores focus. The mute is persisted so a killed-and-restarted service
 *     always unmutes.
 */
class AudioGuard(context: Context, private val store: PrefsGuardStore) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    fun silence() {
        try {
            if (!am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                store.mutedByUs = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "mute failed", e)
        }
        if (focusRequest == null) {
            try {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.requestAudioFocus(req)
                focusRequest = req
            } catch (e: Exception) {
                Log.w(TAG, "audio focus failed", e)
            }
        }
    }

    fun restore() {
        focusRequest?.let {
            try { am.abandonAudioFocusRequest(it) } catch (_: Exception) { }
        }
        focusRequest = null
        if (store.mutedByUs) {
            try {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: Exception) {
                Log.w(TAG, "unmute failed", e)
            }
            store.mutedByUs = false
        }
    }

    companion object { private const val TAG = "AudioGuard" }
}
