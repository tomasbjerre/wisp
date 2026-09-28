package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/tracking.md#session-lifecycle and #auto-pause (auto-resume). */
class PauseWatcherTest {
    @Test
    fun `a fix while paused is stored as noise with the paused reason`() {
        val watcher = PauseWatcher(autoResume = true)

        val outcome = watcher.onFix(fix(lat = 59.0, t = 0))

        assertThat(outcome.shouldResume).isFalse()
        assertThat(outcome.noiseReason).isEqualTo("paused")
    }

    @Test
    fun `a poor-accuracy fix while paused lists both reasons`() {
        val watcher = PauseWatcher(autoResume = true)

        val outcome = watcher.onFix(fix(lat = 59.0, t = 0, accuracy = 45f))

        assertThat(outcome.noiseReason).isEqualTo("poor_accuracy|paused")
    }

    @Test
    fun `an automatic pause resumes once enough real ground has been covered`() {
        val watcher = PauseWatcher(autoResume = true)
        watcher.onFix(fix(lat = 59.0000, t = 0))

        // ~11m per step: past ~30m of movement on the third.
        assertThat(watcher.onFix(fix(lat = 59.0001, t = 5_000)).shouldResume).isFalse()
        assertThat(watcher.onFix(fix(lat = 59.0002, t = 10_000)).shouldResume).isFalse()
        val outcome = watcher.onFix(fix(lat = 59.0003, t = 15_000))

        assertThat(outcome.shouldResume).isTrue()
        assertThat(outcome.noiseReason).isNull()
    }

    @Test
    fun `standing still with jitter never resumes an automatic pause`() {
        val watcher = PauseWatcher(autoResume = true)
        watcher.onFix(fix(lat = 59.00000, t = 0))

        // ~2.2m hops back and forth for a minute: below the jitter floor every time.
        val resumed =
            (1..12).any { step ->
                val lat = if (step % 2 == 1) 59.00002 else 59.00000
                watcher.onFix(fix(lat = lat, t = step * 5_000L)).shouldResume
            }

        assertThat(resumed).isFalse()
    }

    @Test
    fun `poor-accuracy fixes never count toward resuming`() {
        val watcher = PauseWatcher(autoResume = true)
        watcher.onFix(fix(lat = 59.0000, t = 0))

        val resumed =
            (1..10).any { step ->
                watcher.onFix(fix(lat = 59.0000 + step * 0.0001, t = step * 5_000L, accuracy = 45f)).shouldResume
            }

        assertThat(resumed).isFalse()
    }

    @Test
    fun `a manual pause never resumes by itself, however far the person goes`() {
        val watcher = PauseWatcher(autoResume = false)

        val resumed =
            (0..20).any { step ->
                watcher.onFix(fix(lat = 59.0000 + step * 0.0001, t = step * 5_000L)).shouldResume
            }

        assertThat(resumed).isFalse()
    }

    private fun fix(
        lat: Double,
        t: Long,
        accuracy: Float = 5f,
    ) = LocationFix(
        latitude = lat,
        longitude = 18.0,
        accuracyMeters = accuracy,
        speedMps = null,
        timestampMillis = t,
    )
}
