package com.github.tomasbjerre.wisp.ui.tracking

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.tomasbjerre.wisp.location.TrackingService

/**
 * Whether Wisp can show its recording notification — see
 * specs/permissions-and-privacy.md#required-access. Advisory: recording works without it.
 */
class NotificationState internal constructor(
    private val allowed: MutableState<Boolean>,
    private val openSettingsAction: () -> Unit,
) {
    val isAllowed: Boolean get() = allowed.value

    /** For when the system won't ask (again): the app's own notification settings. */
    fun openSettings() = openSettingsAction()
}

/**
 * [askOnEntry]: ask for the permission as this composable is entered (Android 13+, and only if
 * it isn't already allowed) — for a user who granted location before Wisp asked for
 * notifications, and so never sees the request that comes with it. A user who hasn't granted
 * location yet gets it in that same request instead (see [rememberLocationPermissionState]).
 */
@Composable
fun rememberNotificationState(askOnEntry: Boolean): NotificationState {
    val context = LocalContext.current

    fun allowed() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    val isAllowed = remember { mutableStateOf(allowed()) }
    // A permission dialog or the settings screen pauses this one: check again on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isAllowed.value = allowed() }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            isAllowed.value = allowed()
        }
    // A notification posted before this was allowed was dropped: post it again now.
    LaunchedEffect(isAllowed.value) {
        if (isAllowed.value && TrackingService.state.value.isRecording) TrackingService.refreshNotification(context)
    }
    LaunchedEffect(Unit) {
        if (askOnEntry && !isAllowed.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    return remember {
        NotificationState(
            allowed = isAllowed,
            openSettingsAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
    }
}

/** POST_NOTIFICATIONS only exists to ask for from Android 13 — earlier versions have it at install time. */
internal fun notificationPermissionIfAskable(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
