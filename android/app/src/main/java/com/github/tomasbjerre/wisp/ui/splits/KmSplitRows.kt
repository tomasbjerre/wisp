package com.github.tomasbjerre.wisp.ui.splits

import com.github.tomasbjerre.wisp.util.GeoUtils

/** One row of the Km splits view — see specs/ui-flows.md#4-km-splits. */
data class KmSplitRow(
    /** "1", "2", … for complete kilometers; "+0.47" for the trailing partial one. */
    val label: String,
    val durationSeconds: Long,
    val speedMps: Double,
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
                Triple("${index + 1}", seconds, speedMps(METERS_PER_KM, seconds))
            }
        val partial =
            splits.partial?.let {
                val label = "+%.2f".format(it.distanceMeters / METERS_PER_KM)
                Triple(label, it.durationSeconds, speedMps(it.distanceMeters, it.durationSeconds))
            }
        val all = complete + listOfNotNull(partial)
        val fastest = all.maxOfOrNull { it.third } ?: 0.0
        return all.mapIndexed { index, (label, seconds, speed) ->
            KmSplitRow(
                label = label,
                durationSeconds = seconds,
                speedMps = speed,
                relativeSpeed = if (fastest > 0) (speed / fastest).toFloat() else 0f,
                isPartial = partial != null && index == all.lastIndex,
            )
        }
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
