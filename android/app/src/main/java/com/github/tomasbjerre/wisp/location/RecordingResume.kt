package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.util.GeoUtils

/**
 * Where a session that was cut short by the app being killed picks up again — see
 * specs/tracking.md#what-must-survive-interruption. No Android types, no I/O, so it can
 * be unit tested directly against real points.
 */
data class ResumePlan(
    /** The `sequence` of the first point recorded after the resume. */
    val nextSequence: Int,
    val distanceMeters: Double,
    /** Time already recorded; the gap while nothing was recording is not part of it. */
    val elapsedSeconds: Long,
    val steps: Long,
    val maxHeartRateBpm: Int?,
)

object RecordingResume {
    /** Stamped on the first point after a resume — see specs/data-model.md#trackpoint. */
    const val PAUSE_CAUSE_INTERRUPTED = "interrupted"

    /**
     * Null when [points] holds nothing but noise (or nothing at all): movement was never
     * confirmed (see specs/tracking.md#start-gating), so there's nothing to continue and
     * the session is discarded instead.
     */
    fun plan(points: List<TrackPoint>): ResumePlan? {
        val accepted = points.filterNot { it.isNoise }
        if (accepted.isEmpty()) return null
        val summary = GeoUtils.summarize(accepted)
        return ResumePlan(
            nextSequence = points.maxOf { it.sequence } + 1,
            distanceMeters = summary.distanceMeters,
            elapsedSeconds = summary.durationSeconds,
            // The running count at the latest point of any kind, noise included — that's
            // the count the session had reached.
            steps = points.maxBy { it.sequence }.steps,
            maxHeartRateBpm = accepted.mapNotNull { it.heartRateBpm }.maxOrNull(),
        )
    }
}
