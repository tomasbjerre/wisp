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
    fun `a platform-reported speed spike with no real displacement is not movement`() {
        // Regression test for #56: a device held still can report a brief speed spike
        // (GPS multipath/signal noise) well above walking pace while never actually
        // moving — this must not start a session with zero real displacement.
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 0))

        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0, lon = 18.0, t = 1_000, speedMps = 8.0f))

        assertThat(startedMoving).isFalse()
    }

    @Test
    fun `distance and time since the anchor decide movement, regardless of any platform speed`() {
        val gate = MovementGate()
        gate.hasStartedMoving(fix(lat = 59.00000, lon = 18.00000, t = 0, speedMps = 0f))

        // ~11 meters in 5 seconds => ~2.2 m/s, well above walking pace — despite a
        // platform speed reading that on its own would say otherwise.
        val startedMoving = gate.hasStartedMoving(fix(lat = 59.0001, lon = 18.00000, t = 5_000, speedMps = 0f))

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
