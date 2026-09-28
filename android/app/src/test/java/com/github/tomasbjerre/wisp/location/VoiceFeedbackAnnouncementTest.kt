package com.github.tomasbjerre.wisp.location

import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.data.VoiceFeedbackSettings
import com.github.tomasbjerre.wisp.util.GeoUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/voice-feedback.md. */
class VoiceFeedbackAnnouncementTest {
    @Test
    fun `no announcement while voice feedback is off`() {
        val splits = GeoUtils.KmSplits(listOf(300L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 300,
                settings = VoiceFeedbackSettings(enabled = false),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNull()
    }

    @Test
    fun `no announcement when the complete-km count hasn't grown`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 280L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 2,
                elapsedSeconds = 600,
                settings = VoiceFeedbackSettings(enabled = true),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNull()
    }

    @Test
    fun `announces once when a new complete km appears`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 280L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 1,
                elapsedSeconds = 600,
                settings = VoiceFeedbackSettings(enabled = true),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNotNull()
    }

    @Test
    fun `the trailing partial km never triggers an announcement on its own`() {
        // Same complete-km count as before — only the partial km grew.
        val splits = GeoUtils.KmSplits(listOf(300L), GeoUtils.PartialSplit(600.0, 200L))

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 1,
                elapsedSeconds = 500,
                settings = VoiceFeedbackSettings(enabled = true),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNull()
    }

    @Test
    fun `only enabled switches are included, in km-speed-steps-time order`() {
        val splits =
            GeoUtils.KmSplits(
                completeSeconds = listOf(300L),
                partial = null,
                completeSteps = listOf(963L),
            )

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 305,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = true,
                        announceSpeed = true,
                        announceSteps = true,
                        announceElapsedTime = true,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text)
            .isEqualTo("1 kilometer. 5 minutes 0 seconds per kilometer. 963 steps. total time 5 minutes 5 seconds")
    }

    @Test
    fun `says miles, not kilometers, under the imperial unit system`() {
        val splits =
            GeoUtils.KmSplits(
                completeSeconds = listOf(300L),
                partial = null,
                completeSteps = listOf(963L),
            )

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 305,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = true,
                        announceSpeed = true,
                        announceSteps = true,
                        announceElapsedTime = true,
                    ),
                unit = UnitSystem.IMPERIAL,
            )

        assertThat(text).isEqualTo("1 mile. 5 minutes 0 seconds per mile. 963 steps. total time 5 minutes 5 seconds")
    }

    @Test
    fun `switches turned off are left out of the announcement`() {
        val splits =
            GeoUtils.KmSplits(
                completeSeconds = listOf(300L),
                partial = null,
                completeSteps = listOf(963L),
            )

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 305,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = true,
                        announceSpeed = false,
                        announceSteps = false,
                        announceElapsedTime = false,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isEqualTo("1 kilometer")
    }

    @Test
    fun `steps are left out when the session has none, even with the switch on`() {
        val splits = GeoUtils.KmSplits(listOf(300L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 300,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = false,
                        announceSteps = true,
                        announceElapsedTime = false,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNull()
    }

    @Test
    fun `nothing is said when every switch is off, even with voice feedback on`() {
        val splits = GeoUtils.KmSplits(listOf(300L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 300,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = false,
                        announceSteps = false,
                        announceElapsedTime = false,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isNull()
    }

    @Test
    fun `average speed is said as a pace, matching the visual km-splits time, not a km-per-hour figure`() {
        // Regression test for #138: this used to convert the split's duration into a
        // km/h number (e.g. "12.0 kilometers per hour") - a different unit than every
        // other place Wisp shows per-km performance (Detail, Km splits, and the live
        // Tracking panel all show a duration, e.g. "5:13", never a speed).
        val splits = GeoUtils.KmSplits(listOf(313L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                elapsedSeconds = 313,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = true,
                        announceSteps = false,
                        announceElapsedTime = false,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isEqualTo("5 minutes 13 seconds per kilometer")
    }

    @Test
    fun `elapsed time includes hours once the session has run that long`() {
        val splits = GeoUtils.KmSplits(listOf(300L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 0,
                // 1h 2m 3s.
                elapsedSeconds = 3_723,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = false,
                        announceSteps = false,
                        announceElapsedTime = true,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isEqualTo("total time 1 hour 2 minutes 3 seconds")
    }

    @Test
    fun `elapsed time per kilometer says how long that kilometer took, not the session total`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 330L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 1,
                elapsedSeconds = 630,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = false,
                        announceSteps = false,
                        announceKmElapsedTime = true,
                        announceElapsedTime = false,
                    ),
                unit = UnitSystem.METRIC,
            )

        assertThat(text).isEqualTo("kilometer time 5 minutes 30 seconds")
    }

    @Test
    fun `both elapsed times are worded so they cannot be mistaken for each other, in that order`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 330L), null)

        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = 1,
                elapsedSeconds = 630,
                settings =
                    VoiceFeedbackSettings(
                        enabled = true,
                        announceKm = false,
                        announceSpeed = false,
                        announceSteps = false,
                        announceKmElapsedTime = true,
                        announceElapsedTime = true,
                    ),
                unit = UnitSystem.IMPERIAL,
            )

        assertThat(text).isEqualTo("mile time 5 minutes 30 seconds. total time 10 minutes 30 seconds")
    }

    @Test
    fun `elapsed time per kilometer is off by default, total elapsed time is on`() {
        val defaults = VoiceFeedbackSettings()

        assertThat(defaults.announceKmElapsedTime).isFalse()
        assertThat(defaults.announceElapsedTime).isTrue()
    }
}
