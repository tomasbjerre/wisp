package com.github.tomasbjerre.wisp.ui.splits

import com.github.tomasbjerre.wisp.util.GeoUtils

/** One row of the Km splits view — see specs/ui-flows.md#4-km-splits. */
data class KmSplitRow(
    /** "1", "2", … for complete kilometers; "+0.47" for the trailing partial one. */
    val label: String,
    val durationSeconds: Long,
    val speedMps: Double,
    /** Steps taken over this row's distance; null when the session has no steps per split. */
    val steps: Long?,
    /** This row's speed relative to the fastest row's, 0..1 — the length of its bar. */
    val relativeSpeed: Float,
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
    private const val METERS_PER_KM = 1_000.0

    fun rows(splits: GeoUtils.KmSplits): List<KmSplitRow> {
        val complete =
            splits.completeSeconds.mapIndexed { index, seconds ->
                KmSplitRow(
                    label = "${index + 1}",
                    durationSeconds = seconds,
                    speedMps = speedMps(METERS_PER_KM, seconds),
                    steps = splits.completeSteps?.getOrNull(index),
                    relativeSpeed = 0f,
                    isPartial = false,
                )
            }
        val partial =
            splits.partial?.let {
                KmSplitRow(
                    label = "+%.2f".format(it.distanceMeters / METERS_PER_KM),
                    durationSeconds = it.durationSeconds,
                    speedMps = speedMps(it.distanceMeters, it.durationSeconds),
                    steps = it.steps,
                    relativeSpeed = 0f,
                    isPartial = true,
                )
            }
        val all = complete + listOfNotNull(partial)
        val fastest = all.maxOfOrNull { it.speedMps } ?: 0.0
        return all.map { it.copy(relativeSpeed = if (fastest > 0) (it.speedMps / fastest).toFloat() else 0f) }
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

    private fun speedMps(
        meters: Double,
        seconds: Long,
    ) = if (seconds > 0) meters / seconds else 0.0
}
