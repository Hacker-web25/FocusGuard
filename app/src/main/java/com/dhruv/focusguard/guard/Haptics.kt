package com.dhruv.focusguard.guard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Small, deliberate haptic vocabulary. Uses predefined effects where the phone
 * has them (crisper on good motors) and short one-shots otherwise.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Light tick: countdown seconds, holding a button. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, 12, 60)

    /** Firm click: a button that commits to something. */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, 20, 140)

    /** Heavier thud: the lock landing, the timer finishing. */
    fun thud() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 35, 220)

    /** Rising "you did it" pattern for walking away / finishing a sprint. */
    fun success() = waveform(
        longArrayOf(0, 18, 60, 22, 60, 40),
        intArrayOf(0, 90, 0, 150, 0, 255)
    )

    /** Soft swell for breathing phase changes. */
    fun breath() = waveform(
        longArrayOf(0, 40, 40, 40),
        intArrayOf(0, 40, 70, 40)
    )

    private fun predefined(effect: Int, fallbackMs: Long, fallbackAmp: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                v.vibrate(VibrationEffect.createOneShot(fallbackMs, fallbackAmp))
            }
        } catch (_: Exception) { }
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val amps = if (v.hasAmplitudeControl()) amplitudes
            else IntArray(amplitudes.size) { if (amplitudes[it] > 0) 255 else 0 }
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } catch (_: Exception) { }
    }
}
