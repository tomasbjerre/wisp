package com.github.tomasbjerre.wisp.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies the summary stats required by specs/ui-flows.md render sensibly. */
class FormattingTest {
    @Test
    fun `distances under a kilometer are shown in meters`() {
        assertThat(Formatting.distance(250.0)).isEqualTo("250 m")
    }

    @Test
    fun `distances of a kilometer or more are shown in kilometers`() {
        assertThat(Formatting.distance(1500.0)).isEqualTo("1.50 km")
    }

    @Test
    fun `speed is converted from meters per second to kilometers per hour`() {
        assertThat(Formatting.speedKmh(5.0)).isEqualTo("18.0 km/h")
    }

    @Test
    fun `durations under an hour omit the hours component`() {
        assertThat(Formatting.duration(309)).isEqualTo("5:09")
    }

    @Test
    fun `durations of an hour or more include the hours component`() {
        assertThat(Formatting.duration(3605)).isEqualTo("1:00:05")
    }
}
