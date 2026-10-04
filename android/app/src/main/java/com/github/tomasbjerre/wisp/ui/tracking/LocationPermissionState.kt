package com.github.tomasbjerre.wisp.ui.tracking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
    private val requestForeground: () -> Unit,
    private val requestBackground: () -> Unit,
    private val requestBatteryExemptionAction: () -> Unit,
    private val openAppSettingsAction: () -> Unit,
) {
    val hasForeground: Boolean get() = foreground.value
    val hasBackground: Boolean get() = background.value

    /**
     * For when the system won't ask (again): background location's runtime dialog
     * typically only appears once, so a user who already said no to it, or whose OEM
     * never shows it a second time, needs the app's own permission settings instead —
     * same reasoning as [com.github.tomasbjerre.wisp.ui.tracking.NotificationState.openSettings].
     */
    fun openAppSettings() = openAppSettingsAction()

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
            requestForeground()
        } else if (!hasBackground) {
            requestBackground()
        }
    }

    fun requestBatteryExemption() = requestBatteryExemptionAction()
}

@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    fun isGranted(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(context, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

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
    // There's no result callback for this system screen, so just re-check on return.
    val batteryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasBatteryExemption.value = isIgnoringBatteryOptimizations()
        }
    // Same reasoning as batteryLauncher above — the app's own settings screen has no
    // result callback either.
    val appSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasBackground.value = backgroundNotRequired || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

    return remember {
        LocationPermissionState(
            foreground = hasForeground,
            background = hasBackground,
            batteryExemption = hasBatteryExemption,
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
            requestBatteryExemptionAction = {
                batteryLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            openAppSettingsAction = {
                appSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
    }
}
