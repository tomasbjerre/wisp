package com.github.tomasbjerre.wisp.ui.splits

import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlin.math.roundToLong

/** One row of the Km splits view — see specs/ui-flows.md#4-km-splits. */
data class KmSplitRow(
    /** "1", "2", … for complete kilometers; "+0.47" for the trailing partial one. */
    val label: String,
    val durationSeconds: Long,
    val speedMps: Double,
    /** Steps taken over this row's distance; null when the session has no steps per split. */
    val steps: Long?,
    val isPartial: Boolean,
)

/** Fastest and slowest complete kilometer, by 1-based km number. */
data class KmSplitExtremes(
    val fastestKm: Int,
    val fastestSeconds: Long,
    val slowestKm: Int,
    val slowestSeconds: Long,
)

object KmSplitRows {
    fun rows(
        splits: GeoUtils.KmSplits,
        unit: UnitSystem,
    ): List<KmSplitRow> {
        val splitDistanceMeters = unit.splitDistanceMeters
        val complete =
            splits.completeSeconds.mapIndexed { index, seconds ->
                KmSplitRow(
                    label = "${index + 1}",
                    durationSeconds = seconds,
                    speedMps = speedMps(splitDistanceMeters, seconds),
                    steps = splits.completeSteps?.getOrNull(index),
                    isPartial = false,
                )
            }
        val partial =
            splits.partial?.let {
                KmSplitRow(
                    label = "+%.2f".format(it.distanceMeters / splitDistanceMeters),
                    durationSeconds = it.durationSeconds,
                    speedMps = speedMps(it.distanceMeters, it.durationSeconds),
                    steps = it.steps,
                    isPartial = true,
                )
            }
        return complete + listOfNotNull(partial)
    }

    /**
     * Null with fewer than two complete kilometers — nothing to compare. The partial
     * kilometer never counts: over a short distance its pace is too noisy to call it
     * the fastest or slowest.
     */
    fun extremes(splits: GeoUtils.KmSplits): KmSplitExtremes? {
        val seconds = splits.completeSeconds
        if (seconds.size < 2) return null
        val fastest = seconds.indices.minBy { seconds[it] }
        val slowest = seconds.indices.maxBy { seconds[it] }
        return KmSplitExtremes(fastest + 1, seconds[fastest], slowest + 1, seconds[slowest])
    }

    /**
     * The fastest complete kilometer's own time — same "fewer than two complete
     * kilometers, nothing to compare" rule as [extremes], just without needing the
     * partial km around to prove it's excluded. Used where only the km number, not
     * which km it was, is shown (live on Tracking, and in Detail's summary — see
     * specs/tracking.md#km-splits).
     */
    fun fastestSeconds(completeSeconds: List<Long>): Long? {
        if (completeSeconds.size < 2) return null
        return completeSeconds.min()
    }

    /**
     * The average time per complete kilometer — null with no complete kilometers at
     * all. Unlike [fastestSeconds], one complete km is enough to average (there's
     * nothing to compare it against, but it's still a real per-km time).
     */
    fun averageSeconds(completeSeconds: List<Long>): Long? =
        if (completeSeconds.isEmpty()) null else completeSeconds.average().roundToLong()

    private fun speedMps(
        meters: Double,
        seconds: Long,
    ) = if (seconds > 0) meters / seconds else 0.0
}
