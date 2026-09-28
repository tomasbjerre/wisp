package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.UnitSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/permissions-and-privacy.md#required-access: what the recording notification says. */
class TrackingNotificationTextTest {
    private val recording =
        TrackingUiState(isRecording = true, distanceMeters = 2_500.0, elapsedSeconds = 754)

    @Test
    fun `it shows distance and elapsed time while recording`() {
        assertThat(TrackingNotificationText.forState(recording, UnitSystem.METRIC)).isEqualTo("2.50 km · 12:34")
    }

    @Test
    fun `it follows the chosen unit system`() {
        val text = TrackingNotificationText.forState(recording, UnitSystem.IMPERIAL)

        assertThat(text).isEqualTo("1.55 mi · 12:34")
    }

    @Test
    fun `elapsed time past an hour includes the hours`() {
        val text = TrackingNotificationText.forState(recording.copy(elapsedSeconds = 3_725), UnitSystem.METRIC)

        assertThat(text).isEqualTo("2.50 km · 1:02:05")
    }

    @Test
    fun `it says so while paused`() {
        val text = TrackingNotificationText.forState(recording.copy(isPaused = true), UnitSystem.METRIC)

        assertThat(text).isEqualTo("2.50 km · 12:34 · paused")
    }

    @Test
    fun `it says so while waiting for movement`() {
        val waiting = TrackingUiState(isRecording = true, isWaitingForMovement = true)

        assertThat(TrackingNotificationText.forState(waiting, UnitSystem.METRIC))
            .isEqualTo("0 m · 0:00 · waiting to move")
    }
}
