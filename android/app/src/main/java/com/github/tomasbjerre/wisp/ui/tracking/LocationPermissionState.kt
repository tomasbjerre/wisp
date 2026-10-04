package com.github.tomasbjerre.wisp.ui.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** See specs/permissions-and-privacy.md#required-access. */
class LocationPermissionState internal constructor(
    private val foreground: MutableState<Boolean>,
    private val background: MutableState<Boolean>,
    private val batteryExemption: MutableState<Boolean>,
    private val actions: LocationPermissionActions,
) {
    val hasForeground: Boolean get() = foreground.value
    val hasBackground: Boolean get() = background.value

    /**
     * Whether Wisp is exempt from battery optimization, so recording is less likely to be
     * paused while backgrounded (e.g. phone in a pocket) — see
     * specs/permissions-and-privacy.md#required-access. Advisory, not a permission: recording
     * still works if this is declined.
     */
    val hasBatteryExemption: Boolean get() = batteryExemption.value

    /** Requests whichever of foreground/background access is still missing. */
    fun request() {
        if (!hasForeground) {
            actions.requestForeground()
        } else if (!hasBackground) {
            actions.requestBackground()
        }
    }

    fun requestBatteryExemption() = actions.requestBatteryExemption()

    /**
     * For when the system won't ask (again): background location's runtime dialog
     * typically only appears once, so a user who already said no to it, or whose OEM
     * never shows it a second time, needs the app's own permission settings instead —
     * same reasoning as [NotificationState.openSettings].
     */
    fun openAppSettings() = actions.openAppSettings()
}

/** The callbacks [LocationPermissionState] exposes, grouped to keep its constructor short. */
internal class LocationPermissionActions(
    val requestForeground: () -> Unit,
    val requestBackground: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    fun isGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(): Boolean = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val backgroundNotRequired = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val hasForeground = remember { mutableStateOf(isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    val hasBackground =
        remember {
            mutableStateOf(backgroundNotRequired || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    val hasBatteryExemption = remember { mutableStateOf(isIgnoringBatteryOptimizations()) }

    val backgroundLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasBackground.value = granted || backgroundNotRequired
        }
    val foregroundLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasForeground.value = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (hasForeground.value && !backgroundNotRequired) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    // These two system screens have no result callback of their own, so just re-check
    // the relevant state on return.
    val batteryLauncher = rememberSettingsLauncher { hasBatteryExemption.value = isIgnoringBatteryOptimizations() }
    val appSettingsLauncher =
        rememberSettingsLauncher {
            hasBackground.value = backgroundNotRequired || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

    val actions =
        LocationPermissionActions(
            requestForeground = {
                // Activity recognition (see specs/permissions-and-privacy.md#required-access)
                // is bundled into this same system dialog — it's opportunistic, not
                // gated/tracked like the location permissions above: nothing here reads its
                // result, a decline just means that session has no step count.
                foregroundLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                    ) + notificationPermissionIfAskable(),
                )
            },
            requestBackground = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            requestBatteryExemption = {
                batteryLauncher.launch(settingsIntent(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            },
            openAppSettings = {
                appSettingsLauncher.launch(settingsIntent(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
            },
        )

    return remember { LocationPermissionState(hasForeground, hasBackground, hasBatteryExemption, actions) }
}

@Composable
private fun rememberSettingsLauncher(onReturn: () -> Unit): ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onReturn() }

private fun settingsIntent(
    context: Context,
    action: String,
) = Intent(action, Uri.parse("package:${context.packageName}"))
