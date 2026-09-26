package com.github.tomasbjerre.wisp.location

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
            )

        assertThat(text).isEqualTo("1 kilometer. 12.0 kilometers per hour. 963 steps. 5 minutes 5 seconds")
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
            )

        assertThat(text).isNull()
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
            )

        assertThat(text).isEqualTo("1 hour 2 minutes 3 seconds")
    }
}
