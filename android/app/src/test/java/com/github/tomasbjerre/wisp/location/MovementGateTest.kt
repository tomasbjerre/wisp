package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#start-gating. */
class MovementGateTest {
    @Test
    fun `the first fix ever seen is only an anchor, not movement`() {
        val gate = MovementGate()

        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 0))

        assertThat(startedMoving).isFalse()
    }

    @Test
    fun `standing still after the anchor is not movement`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 0))

        // Same spot, several seconds later.
        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 5_000))

        assertThat(startedMoving).isFalse()
    }

    @Test
    fun `platform-reported speed at or above walking pace counts as movement`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 0))

        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 1_000, speedMps = 1.2f))

        assertThat(startedMoving).isTrue()
    }

    @Test
    fun `platform-reported speed below walking pace is not movement`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 0))

        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 1_000, speedMps = 0.2f))

        assertThat(startedMoving).isFalse()
    }

    @Test
    fun `without a platform speed, distance and time since the anchor are used instead`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.00000, lon = 18.00000, t = 0, speedMps = null))

        // ~11 meters in 5 seconds => ~2.2 m/s, well above walking pace.
        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0001, lon = 18.00000, t = 5_000, speedMps = null))

        assertThat(startedMoving).isTrue()
    }

    @Test
    fun `distance is measured from the fixed anchor, not the previous fix`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.00000, lon = 18.00000, t = 0, speedMps = null))
        // Tiny jitter, not real movement — must not become the new anchor.
        gate.hasStartedMoving(fix(lat = 59.000005, lon = 18.00000, t = 1_000, speedMps = null))

        // Jitter-sized hop from the *previous* fix, but still tiny from the original anchor.
        val startedMoving = gate.hasStartedMoving(fix(lat = 59.00001, lon = 18.00000, t = 2_000, speedMps = null))

        assertThat(startedMoving).isFalse()
    }

    private fun fix(
        lat: Double,
        lon: Double,
        t: Long,
        speedMps: Float? = null,
    ) = LocationFix(
        latitude = lat,
        longitude = lon,
        accuracyMeters = 5f,
        speedMps = speedMps,
        timestampMillis = t,
    )
}
