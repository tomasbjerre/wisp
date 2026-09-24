package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#auto-pause. */
class StationaryGateTest {
    @Test
    fun `the first fix ever seen only anchors, it does not trigger auto-pause`() {
        val gate = StationaryGate()

        val shouldAutoPause = gate.onFix(fix(lat = 59.0, lon = 18.0, t = 0))

        assertThat(shouldAutoPause).isFalse()
    }

    @Test
    fun `standing still for less than the idle threshold does not trigger auto-pause`() {
        val gate = StationaryGate()
        gate.onFix(fix(lat = 59.0, lon = 18.0, t = 0))

        val shouldAutoPause = gate.onFix(fix(lat = 59.0, lon = 18.0, t = StationaryGate.IDLE_THRESHOLD_MILLIS - 1))

        assertThat(shouldAutoPause).isFalse()
    }

    @Test
    fun `standing still for at least the idle threshold triggers auto-pause`() {
        val gate = StationaryGate()
        gate.onFix(fix(lat = 59.0, lon = 18.0, t = 0))

        val shouldAutoPause = gate.onFix(fix(lat = 59.0, lon = 18.0, t = StationaryGate.IDLE_THRESHOLD_MILLIS))

        assertThat(shouldAutoPause).isTrue()
    }

    @Test
    fun `continuing to move never triggers auto-pause`() {
        val gate = StationaryGate()
        gate.onFix(fix(lat = 59.0000, lon = 18.0000, t = 0))

        // A brisk walk, well past what would otherwise be the idle threshold.
        var shouldAutoPause = false
        var lat = 59.0000
        var t = 0L
        repeat(10) {
            lat += 0.0001
            t += 5_000
            shouldAutoPause = gate.onFix(fix(lat = lat, lon = 18.0000, t = t))
        }

        assertThat(shouldAutoPause).isFalse()
    }

    @Test
    fun `movement resets the idle clock, so a later stop needs its own full threshold`() {
        val gate = StationaryGate()
        gate.onFix(fix(lat = 59.0000, lon = 18.0000, t = 0))
        // Confirmed moving partway through what would've been the idle window: ~22m in
        // 14s => ~1.6 m/s, comfortably above walking pace.
        gate.onFix(fix(lat = 59.0002, lon = 18.0000, t = StationaryGate.IDLE_THRESHOLD_MILLIS - 1_000))

        // Stopped again — only 1s past the reset, nowhere near a full threshold yet.
        val shouldAutoPause = gate.onFix(fix(lat = 59.0002, lon = 18.0000, t = StationaryGate.IDLE_THRESHOLD_MILLIS))

        assertThat(shouldAutoPause).isFalse()
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
