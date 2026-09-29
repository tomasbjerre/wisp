package com.github.tomasbjerre.wisp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.github.tomasbjerre.wisp.ui.WispApp
import com.github.tomasbjerre.wisp.ui.theme.WispTheme

class MainActivity : ComponentActivity() {
    // Counts the times the recording notification was tapped — see EXTRA_OPEN_TRACKING. A
    // counter, not a flag, so tapping it twice in a row is two requests.
    private var openTrackingRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) noteOpenTrackingRequest(intent)
        setContent {
            WispTheme {
                val app = application as WispApplication
                WispApp(
                    repository = app.repository,
                    voiceFeedbackPreferences = app.voiceFeedbackPreferences,
                    unitPreferences = app.unitPreferences,
                    heartRatePreferences = app.heartRatePreferences,
                    openTrackingRequests = openTrackingRequests,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // No setIntent(): nothing reads it after this, and replacing it would stop the
        // instrumentation's ActivityScenario recognising this activity as the one it launched.
        noteOpenTrackingRequest(intent)
    }

    private fun noteOpenTrackingRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRACKING, false) == true) openTrackingRequests++
    }

    companion object {
        /** Set on the recording notification's intent — see specs/permissions-and-privacy.md#required-access. */
        const val EXTRA_OPEN_TRACKING = "com.github.tomasbjerre.wisp.extra.OPEN_TRACKING"
    }
}
