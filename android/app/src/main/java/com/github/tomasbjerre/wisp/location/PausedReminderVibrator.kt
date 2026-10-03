package com.github.tomasbjerre.wisp.location

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Thin wrapper around the device's vibrator — see
 * specs/tracking.md#paused-session-reminder. Not unit tested directly, same as
 * [VoiceFeedbackSpeaker]/[LocationTracker]: it's a live platform API with nothing to assert
 * against outside a real device. [PausedReminderGate] carries the testable logic (when to
 * pulse and how many times); this class only ever pulses when told to.
 */
class PausedReminderVibrator(
    context: Context,
) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /**
     * No-op on a device with no vibrator — see specs/tracking.md#paused-session-reminder:
     * the reminder is best effort and never something that blocks or errors a recording.
     */
    fun pulse() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(PULSE_DURATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        /**
         * "Briefly" — see specs/tracking.md#paused-session-reminder, where the exact
         * duration is left to the implementation. Long enough to feel through a pocket,
         * short enough not to be a buzz of its own.
         */
        private const val PULSE_DURATION_MILLIS = 200L
    }
}
