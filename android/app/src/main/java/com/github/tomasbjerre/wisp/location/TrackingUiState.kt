package com.github.tomasbjerre.wisp.location

data class LatLon(
    val latitude: Double,
    val longitude: Double,
)

/** Live state broadcast by [TrackingService] while a session is being recorded. */
data class TrackingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val sessionId: Long? = null,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val currentSpeedMps: Double = 0.0,
    val route: List<LatLon> = emptyList(),
)
