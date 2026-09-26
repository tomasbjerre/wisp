package com.github.tomasbjerre.wisp.data

/**
 * See specs/units.md — the one other piece of user-facing configuration in Wisp,
 * alongside [VoiceFeedbackSettings] (see specs/overview.md#design-principle). Metric
 * is the default.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    /** The distance one complete split covers — see specs/tracking.md#km-splits. */
    val splitDistanceMeters: Double
        get() = if (this == METRIC) METERS_PER_KM else METERS_PER_MILE

    val distanceAbbreviation: String
        get() = if (this == METRIC) "km" else "mi"

    val shortDistanceAbbreviation: String
        get() = if (this == METRIC) "m" else "ft"

    val speedAbbreviation: String
        get() = if (this == METRIC) "km/h" else "mph"

    val distanceWordSingular: String
        get() = if (this == METRIC) "kilometer" else "mile"

    val distanceWordPlural: String
        get() = if (this == METRIC) "kilometers" else "miles"

    val speedWords: String
        get() = if (this == METRIC) "kilometers per hour" else "miles per hour"

    companion object {
        const val METERS_PER_KM = 1_000.0
        const val METERS_PER_MILE = 1_609.34
        const val FEET_PER_METER = 3.28084
    }
}
