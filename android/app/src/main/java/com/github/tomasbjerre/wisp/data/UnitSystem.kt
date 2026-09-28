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

    /** See specs/units.md — body weight is entered and shown in this, stored in kilograms. */
    val weightAbbreviation: String
        get() = if (this == METRIC) "kg" else "lb"

    fun kilogramsToDisplay(kilograms: Double): Double = if (this == METRIC) kilograms else kilograms * POUNDS_PER_KG

    fun displayToKilograms(displayed: Double): Double = if (this == METRIC) displayed else displayed / POUNDS_PER_KG

    val distanceWordSingular: String
        get() = if (this == METRIC) "kilometer" else "mile"

    val distanceWordPlural: String
        get() = if (this == METRIC) "kilometers" else "miles"

    companion object {
        const val METERS_PER_KM = 1_000.0
        const val METERS_PER_MILE = 1_609.34
        const val FEET_PER_METER = 3.28084
        const val POUNDS_PER_KG = 2.20462
    }
}
