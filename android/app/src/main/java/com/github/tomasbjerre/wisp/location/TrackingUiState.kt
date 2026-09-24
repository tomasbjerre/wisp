package com.github.tomasbjerre.wisp.location

data class LatLon(
    val latitude: Double,
    val longitude: Double,
)

/** Live state broadcast by [TrackingService] while a session is being recorded. */
data class TrackingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    // See specs/tracking.md#start-gating.
    val isLocating: Boolean = false,
    val isWaitingForMovement: Boolean = false,
    // True right after Stop discarded a session that never saw movement — see
    // specs/tracking.md#start-gating and TrackingService.stop.
    val wasDiscarded: Boolean = false,
    val sessionId: Long? = null,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val currentSpeedMps: Double = 0.0,
    val route: List<LatLon> = emptyList(),
    // See specs/tracking.md#step-count. 0 both when there's genuinely no steps yet and
    // when there's no sensor/permission — the UI treats those identically (omit).
    val steps: Long = 0,
    // See specs/tracking.md#km-splits. Only the most recently completed split — the
    // full list is Detail's job, live Tracking only ever needs "how was that last km".
    val latestKmSplitSeconds: Long? = null,
)
