package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.UnitSystem
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
        unit: UnitSystem,
    ): String? {
        if (!settings.enabled) return null
        val completeCount = splits.completeSeconds.size
        if (completeCount <= previousCompleteCount) return null
        val index = completeCount - 1
        return phrase(
            splitNumber = completeCount,
            lastSplitDurationSeconds = splits.completeSeconds[index],
            lastSplitSteps = splits.completeSteps?.getOrNull(index),
            elapsedSeconds = elapsedSeconds,
            settings = settings,
            unit = unit,
        ).ifBlank { null }
    }

    private fun phrase(
        splitNumber: Int,
        lastSplitDurationSeconds: Long,
        lastSplitSteps: Long?,
        elapsedSeconds: Long,
        settings: VoiceFeedbackSettings,
        unit: UnitSystem,
    ): String {
        val parts = mutableListOf<String>()
        if (settings.announceKm) {
            parts +=
                if (splitNumber == 1) "1 ${unit.distanceWordSingular}" else "$splitNumber ${unit.distanceWordPlural}"
        }
        if (settings.announceSpeed && lastSplitDurationSeconds > 0) {
            // Said as pace (time per unit distance, e.g. "5 minutes 13 seconds per
            // kilometer") to match how Wisp shows this everywhere else it appears (the
            // live "Last kilometer"/"Fastest kilometer" stats and the Km splits view are
            // all a duration, never a km/h figure) — see #138.
            parts += "${durationPhrase(lastSplitDurationSeconds)} per ${unit.distanceWordSingular}"
        }
        if (settings.announceSteps && lastSplitSteps != null) parts += "$lastSplitSteps steps"
        // Said differently from each other on purpose: with both switches on, two bare
        // durations in a row would leave a listener guessing which is which — see #148.
        if (settings.announceKmElapsedTime && lastSplitDurationSeconds > 0) {
            parts += "${unit.distanceWordSingular} time ${durationPhrase(lastSplitDurationSeconds)}"
        }
        if (settings.announceElapsedTime) parts += "total time ${durationPhrase(elapsedSeconds)}"
        return parts.joinToString(". ")
    }

    private fun durationPhrase(seconds: Long): String {
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
}
