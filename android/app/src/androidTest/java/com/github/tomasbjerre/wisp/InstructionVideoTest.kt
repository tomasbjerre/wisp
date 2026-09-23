package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test — walks the real app through its main flows while the CI
 * workflow records the screen (see android/README.md#play-store-release), producing
 * the "clearly shows usage of the app" video Play Console asks for.
 *
 * Grants its own location permissions via [android.app.UiAutomation] rather than
 * tapping through the system permission dialog, so the video shows the actual
 * Tracking screen instead of a permission prompt. The CI workflow fixes the
 * emulator's location before this test runs (see release_android.yml) so
 * Tracking has a real position to show rather than hanging on "Finding your
 * location…" (see specs/tracking.md#start-gating).
 */
@RunWith(AndroidJUnit4::class)
class InstructionVideoTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun recordWalkthrough() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(APP_PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
        instrumentation.uiAutomation.grantRuntimePermission(
            APP_PACKAGE,
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        )

        composeRule.waitForIdle()
        val app = instrumentation.targetContext.applicationContext as WispApplication
        runBlocking { seedSession(app, daysAgo = 1, durationSeconds = 1_620, speedMps = 3.2) }
        composeRule.waitForIdle()
        Thread.sleep(PAUSE_MILLIS)

        // Home -> a past session's Detail screen.
        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()
        composeRule.waitForIdle()
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)

        // Back to Home, then start a new session.
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        Thread.sleep(PAUSE_MILLIS)

        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = LOCATE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Stop").fetchSemanticsNodes().isNotEmpty()
        }
        // Let "waiting for movement" actually show on screen for a moment (see
        // specs/tracking.md#start-gating) before wrapping up.
        Thread.sleep(TRACKING_SETTLE_MILLIS)

        composeRule.onNodeWithText("Stop").performClick()
        composeRule.waitForIdle()
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)
    }

    private companion object {
        const val APP_PACKAGE = "com.github.tomasbjerre.wisp"
        const val PAUSE_MILLIS = 1_500L
        const val MAP_TILE_SETTLE_MILLIS = 3_000L
        const val TRACKING_SETTLE_MILLIS = 4_000L
        const val LOCATE_TIMEOUT_MILLIS = 10_000L
    }
}
