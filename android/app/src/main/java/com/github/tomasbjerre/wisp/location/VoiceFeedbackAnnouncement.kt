package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.VoiceFeedbackSettings
import com.github.tomasbjerre.wisp.util.GeoUtils

/**
 * See specs/voice-feedback.md — decides whether a just-updated [GeoUtils.KmSplits]
 * warrants an announcement, and builds what to say, without touching Android's
 * TextToSpeech API (see [VoiceFeedbackSpeaker]) so this is unit-testable on its own,
 * the same split as [TrackRecorder] (pure) vs [LocationTracker] (the live API).
 */
object VoiceFeedbackAnnouncement {
    /**
     * Null unless [splits] just gained a new complete kilometer compared to
     * [previousCompleteCount] — fires at most once per completed km, never for the
     * partial km, and never repeats for one already announced. Also null with voice
     * feedback off, or with every announcement switch off (nothing to say).
     */
    fun forNewlyCompletedKm(
        splits: GeoUtils.KmSplits,
        previousCompleteCount: Int,
        elapsedSeconds: Long,
        settings: VoiceFeedbackSettings,
    ): String? {
        if (!settings.enabled) return null
        val completeCount = splits.completeSeconds.size
        if (completeCount <= previousCompleteCount) return null
        val index = completeCount - 1
        return phrase(
            km = completeCount,
            lastKmDurationSeconds = splits.completeSeconds[index],
            lastKmSteps = splits.completeSteps?.getOrNull(index),
            elapsedSeconds = elapsedSeconds,
            settings = settings,
        ).ifBlank { null }
    }

    private fun phrase(
        km: Int,
        lastKmDurationSeconds: Long,
        lastKmSteps: Long?,
        elapsedSeconds: Long,
        settings: VoiceFeedbackSettings,
    ): String {
        val parts = mutableListOf<String>()
        if (settings.announceKm) parts += if (km == 1) "1 kilometer" else "$km kilometers"
        if (settings.announceSpeed && lastKmDurationSeconds > 0) {
            val kmh = SECONDS_PER_HOUR / lastKmDurationSeconds
            parts += "%.1f kilometers per hour".format(kmh)
        }
        if (settings.announceSteps && lastKmSteps != null) parts += "$lastKmSteps steps"
        if (settings.announceElapsedTime) parts += elapsedTimePhrase(elapsedSeconds)
        return parts.joinToString(". ")
    }

    private fun elapsedTimePhrase(seconds: Long): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val remainingSeconds = seconds % 60
        return buildString {
            if (hours > 0) append("${unit(hours, "hour")} ")
            if (hours > 0 || minutes > 0) append("${unit(minutes, "minute")} ")
            append(unit(remainingSeconds, "second"))
        }
    }

    private fun unit(
        value: Long,
        singular: String,
    ) = "$value $singular" + if (value == 1L) "" else "s"

    private const val SECONDS_PER_HOUR = 3_600.0
}
