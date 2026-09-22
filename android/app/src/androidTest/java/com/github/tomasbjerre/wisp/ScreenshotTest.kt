package com.github.tomasbjerre.wisp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test — drives the real app against seeded (not live-GPS) data to
 * capture screenshots for the Play Store listing and README. See
 * android/README.md#screenshots. Output lands under /sdcard/wisp-screenshots (not the
 * app's own storage, which `connectedAndroidTest` wipes by uninstalling the app when
 * the run finishes) — the CI workflow pulls it from there via `adb pull`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun captureScreenshots() {
        dismissSystemAnrIfPresent()
        composeRule.waitForIdle()
        screenshot("1-home-empty")

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WispApplication
        runBlocking {
            seedSession(app, daysAgo = 1, durationSeconds = 1_620, speedMps = 3.2)
            seedSession(app, daysAgo = 4, durationSeconds = 3_120, speedMps = 4.0)
        }
        composeRule.waitForIdle()
        screenshot("2-home-history")

        composeRule.onAllNodesWithTag(TestTags.HISTORY_ROW).onFirst().performClick()
        composeRule.waitForIdle()
        // osmdroid tiles load asynchronously over the network; give them a moment to
        // arrive before capturing, since Compose idling doesn't know about that.
        Thread.sleep(MAP_TILE_SETTLE_MILLIS)
        screenshot("3-detail")
    }

    // Via the shell, not app-code File I/O: scoped storage silently blocks the app
    // process itself from writing raw /sdcard paths, but the shell (uiautomator's
    // executeShellCommand) isn't subject to that.
    private fun screenshot(name: String) {
        dismissSystemAnrIfPresent()
        device.executeShellCommand("mkdir -p $SCREENSHOT_DIR")
        device.executeShellCommand("screencap -p $SCREENSHOT_DIR/$name.png")
    }

    /** Occasionally a "System UI isn't responding" dialog covers the screen on a loaded
     * (e.g. software-rendered) emulator — dismiss it rather than capture it by accident. */
    private fun dismissSystemAnrIfPresent() {
        val waitButton = device.findObject(UiSelector().textContains("Wait"))
        if (waitButton.exists()) {
            waitButton.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val SCREENSHOT_DIR = "/sdcard/wisp-screenshots"
        const val MAP_TILE_SETTLE_MILLIS = 3_000L
    }
}
