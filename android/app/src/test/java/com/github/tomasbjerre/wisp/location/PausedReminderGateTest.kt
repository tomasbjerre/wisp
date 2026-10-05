package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#paused-session-reminder. */
class PausedReminderGateTest {
    @Test
    fun `nothing is pulsed before a full minute of being paused has passed`() {
        val gate = gatePausedAt(0)

        assertThat(pulses(gate, from = 0, to = MINUTE - 1)).isZero()
    }

    @Test
    fun `one pulse is due once a minute of being paused has passed`() {
        val gate = gatePausedAt(0)

        assertThat(pulses(gate, from = 0, to = MINUTE)).isEqualTo(1)
    }

    @Test
    fun `pulses keep coming once a minute for as long as the session stays paused`() {
        val gate = gatePausedAt(0)

        assertThat(pulses(gate, from = 0, to = 5 * MINUTE)).isEqualTo(5)
    }

    @Test
    fun `it stops after twenty pulses rather than buzzing forever`() {
        val gate = gatePausedAt(0)

        // Far past the twenty minutes twenty pulses would otherwise take.
        assertThat(pulses(gate, from = 0, to = 60 * MINUTE)).isEqualTo(MAX_PULSES)
    }

    @Test
    fun `reports itself exhausted once the twenty pulses are spent, so the caller can stop checking`() {
        val gate = gatePausedAt(0)
        assertThat(gate.isExhausted).isFalse()

        pulses(gate, from = 0, to = 20 * MINUTE)

        assertThat(gate.pulses).isEqualTo(MAX_PULSES)
        assertThat(gate.isExhausted).isTrue()
    }

    @Test
    fun `nothing is pulsed while the app is on screen`() {
        val gate = gatePausedAt(0)

        assertThat(pulses(gate, from = 0, to = 10 * MINUTE, suppressed = true)).isZero()
    }

    @Test
    fun `the first pulse after the app is put away comes a full minute later, not right away`() {
        val gate = gatePausedAt(0)
        // Held open for a long stretch, so the schedule has stood by the whole time.
        pulses(gate, from = 0, to = PUT_AWAY_AT, suppressed = true)

        val justAfterPuttingAway = gate.onTick(nowElapsedRealtime = PUT_AWAY_AT + 1, suppressed = false)
        val oneMinuteAfter = gate.onTick(nowElapsedRealtime = PUT_AWAY_AT + MINUTE, suppressed = false)

        assertThat(justAfterPuttingAway).isFalse()
        assertThat(oneMinuteAfter).isTrue()
    }

    @Test
    fun `time spent with the app on screen is not charged against the twenty pulses`() {
        val gate = gatePausedAt(0)
        // An hour with the app open, in which no pulse could have been felt at all.
        pulses(gate, from = 0, to = HOUR, suppressed = true)

        assertThat(pulses(gate, from = HOUR, to = HOUR + 20 * MINUTE)).isEqualTo(MAX_PULSES)
    }

    @Test
    fun `a gate covers exactly one paused stretch, so pausing again starts a fresh set of pulses`() {
        // A first pause left to run out its entire allowance.
        assertThat(pulses(gatePausedAt(0), from = 0, to = 60 * MINUTE)).isEqualTo(MAX_PULSES)

        // A second pause, later in the same session, gets its own full allowance.
        val secondPause = gatePausedAt(HOUR)
        assertThat(pulses(secondPause, from = HOUR, to = HOUR + 20 * MINUTE)).isEqualTo(MAX_PULSES)
    }

    private fun gatePausedAt(pauseStartedElapsedRealtime: Long) = PausedReminderGate(pauseStartedElapsedRealtime)

    /**
     * Drives [gate] once a second, as TrackingService's reminder loop does, and counts the
     * pulses it asks for.
     */
    private fun pulses(
        gate: PausedReminderGate,
        from: Long,
        to: Long,
        suppressed: Boolean = false,
    ): Int {
        var count = 0
        var now = from
        while (now <= to) {
            if (gate.onTick(nowElapsedRealtime = now, suppressed = suppressed)) count++
            now += TICK_MILLIS
        }
        return count
    }

    private companion object {
        const val MINUTE = PausedReminderGate.PULSE_INTERVAL_MILLIS
        const val MAX_PULSES = PausedReminderGate.MAX_PULSES

        /** The 1s cadence of TrackingService's reminder loop. */
        const val TICK_MILLIS = 1_000L

        // Where the app is put away, and where an hour-long stretch of it being open ends.
        const val PUT_AWAY_AT = 5 * MINUTE
        const val HOUR = 60 * MINUTE
    }
}
