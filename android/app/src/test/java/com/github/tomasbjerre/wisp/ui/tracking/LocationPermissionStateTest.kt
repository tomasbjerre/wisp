package com.github.tomasbjerre.wisp.ui.tracking

import androidx.compose.runtime.mutableStateOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/ui-flows.md#2-tracking-active-recording and permissions-and-privacy.md#required-access. */
class LocationPermissionStateTest {
    private fun actions(
        onRequestForeground: () -> Unit = {},
        onRequestBackground: () -> Unit = {},
    ) = LocationPermissionActions(
        requestForeground = onRequestForeground,
        requestBackground = onRequestBackground,
        requestBatteryExemption = {},
        openAppSettings = {},
    )

    @Test
    fun `request asks for foreground first when it's missing`() {
        var requestedForeground = false
        var requestedBackground = false
        val state =
            LocationPermissionState(
                foreground = mutableStateOf(false),
                background = mutableStateOf(false),
                batteryExemption = mutableStateOf(false),
                backgroundRationalePending = mutableStateOf(false),
                actions =
                    actions(
                        onRequestForeground = { requestedForeground = true },
                        onRequestBackground = { requestedBackground = true },
                    ),
            )

        state.request()

        assertThat(requestedForeground).isTrue()
        assertThat(requestedBackground).isFalse()
    }

    @Test
    fun `request asks for background once foreground is granted, clearing the pending rationale`() {
        var requestedBackground = false
        val rationalePending = mutableStateOf(true)
        val state =
            LocationPermissionState(
                foreground = mutableStateOf(true),
                background = mutableStateOf(false),
                batteryExemption = mutableStateOf(false),
                backgroundRationalePending = rationalePending,
                actions = actions(onRequestBackground = { requestedBackground = true }),
            )

        state.request()

        assertThat(requestedBackground).isTrue()
        assertThat(rationalePending.value).isFalse()
    }

    @Test
    fun `request does nothing once both foreground and background are granted`() {
        var requestedForeground = false
        var requestedBackground = false
        val state =
            LocationPermissionState(
                foreground = mutableStateOf(true),
                background = mutableStateOf(true),
                batteryExemption = mutableStateOf(false),
                backgroundRationalePending = mutableStateOf(false),
                actions =
                    actions(
                        onRequestForeground = { requestedForeground = true },
                        onRequestBackground = { requestedBackground = true },
                    ),
            )

        state.request()

        assertThat(requestedForeground).isFalse()
        assertThat(requestedBackground).isFalse()
    }

    @Test
    fun `the background rationale is needed only right after a fresh foreground grant still missing background`() {
        assertThat(
            needsBackgroundRationaleAfterForegroundGrant(
                foregroundGranted = true,
                backgroundRequiredByPlatform = true,
                alreadyHasBackground = false,
            ),
        ).isTrue()
    }

    @Test
    fun `the background rationale is not needed when the foreground grant itself failed`() {
        assertThat(
            needsBackgroundRationaleAfterForegroundGrant(
                foregroundGranted = false,
                backgroundRequiredByPlatform = true,
                alreadyHasBackground = false,
            ),
        ).isFalse()
    }

    @Test
    fun `the background rationale is not needed on platform versions that don't require it`() {
        assertThat(
            needsBackgroundRationaleAfterForegroundGrant(
                foregroundGranted = true,
                backgroundRequiredByPlatform = false,
                alreadyHasBackground = false,
            ),
        ).isFalse()
    }

    @Test
    fun `the background rationale is not needed once background is already granted`() {
        assertThat(
            needsBackgroundRationaleAfterForegroundGrant(
                foregroundGranted = true,
                backgroundRequiredByPlatform = true,
                alreadyHasBackground = true,
            ),
        ).isFalse()
    }
}
