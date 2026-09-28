package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.ui.Formatting

/**
 * The line shown in the recording notification — see specs/permissions-and-privacy.md#required-access.
 * Pure, so it can be tested directly.
 */
object TrackingNotificationText {
    fun forState(
        state: TrackingUiState,
        unit: UnitSystem,
    ): String {
        val suffix =
            when {
                state.isWaitingForMovement -> " · waiting to move"
                state.isPaused -> " · paused"
                else -> ""
            }
        val distance = Formatting.distance(state.distanceMeters, unit)
        return "$distance · ${Formatting.duration(state.elapsedSeconds)}$suffix"
    }
}
