package com.github.tomasbjerre.wisp.util

import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.data.Session
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/calories.md. */
class CaloriesCalculatorTest {
    private fun mps(kmh: Double) = kmh / 3.6

    @Test
    fun `calories are MET times weight times hours`() {
        // Running 10 km in one hour is 10 km/h, which is MET 9.8: 9.8 * 70 kg * 1 h.
        val kcal = CaloriesCalculator.kilocalories(ActivityType.RUNNING, 70.0, 10_000.0, 3_600)

        assertThat(kcal).isCloseTo(686.0, within(0.001))
    }

    @Test
    fun `time is the recorded duration, so half an hour burns half as much at the same speed`() {
        val kcal = CaloriesCalculator.kilocalories(ActivityType.CYCLING, 75.0, 10_000.0, 1_800)

        // 20 km/h is MET 8.0: 8.0 * 75 kg * 0.5 h.
        assertThat(kcal).isCloseTo(300.0, within(0.001))
    }

    @Test
    fun `a session with no duration has no calories`() {
        assertThat(CaloriesCalculator.kilocalories(ActivityType.WALKING, 70.0, 0.0, 0)).isNull()
    }

    @Test
    fun `a speed falls in the row with the highest lower bound it reaches`() {
        assertThat(CaloriesCalculator.met(ActivityType.WALKING, mps(5.0))).isEqualTo(3.5)
        assertThat(CaloriesCalculator.met(ActivityType.WALKING, mps(4.8))).isEqualTo(3.5)
        assertThat(CaloriesCalculator.met(ActivityType.WALKING, mps(4.79))).isEqualTo(3.0)
    }

    @Test
    fun `standing still is the first row of each table`() {
        assertThat(CaloriesCalculator.met(ActivityType.WALKING, 0.0)).isEqualTo(2.0)
        assertThat(CaloriesCalculator.met(ActivityType.RUNNING, 0.0)).isEqualTo(6.0)
        assertThat(CaloriesCalculator.met(ActivityType.CYCLING, 0.0)).isEqualTo(4.0)
    }

    @Test
    fun `the highest row applies to any speed above it`() {
        assertThat(CaloriesCalculator.met(ActivityType.WALKING, mps(30.0))).isEqualTo(8.3)
        assertThat(CaloriesCalculator.met(ActivityType.RUNNING, mps(40.0))).isEqualTo(19.0)
        assertThat(CaloriesCalculator.met(ActivityType.CYCLING, mps(60.0))).isEqualTo(15.8)
    }

    @Test
    fun `the same speed is a different MET for each activity type`() {
        val speed = mps(10.0)

        assertThat(CaloriesCalculator.met(ActivityType.WALKING, speed)).isEqualTo(8.3)
        assertThat(CaloriesCalculator.met(ActivityType.RUNNING, speed)).isEqualTo(9.8)
        assertThat(CaloriesCalculator.met(ActivityType.CYCLING, speed)).isEqualTo(4.0)
    }

    @Test
    fun `a session calculates from the weight and activity type it stored`() {
        val session =
            Session(
                startedAt = 0,
                distanceMeters = 5_000.0,
                durationSeconds = 3_600,
                activityType = "walking",
                weightKg = 80.0,
            )

        // 5 km/h is MET 3.5: 3.5 * 80 kg * 1 h.
        assertThat(session.kilocalories()).isCloseTo(280.0, within(0.001))
    }

    @Test
    fun `a session with no stored weight has no calories, however much time it has`() {
        val session =
            Session(startedAt = 0, distanceMeters = 5_000.0, durationSeconds = 3_600, activityType = "walking")

        assertThat(session.kilocalories()).isNull()
    }

    @Test
    fun `a session with no activity type has no calories`() {
        val session = Session(startedAt = 0, distanceMeters = 5_000.0, durationSeconds = 3_600, weightKg = 80.0)

        assertThat(session.kilocalories()).isNull()
    }
}
