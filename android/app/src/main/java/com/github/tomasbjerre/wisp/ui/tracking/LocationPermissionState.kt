package com.github.tomasbjerre.wisp.ui.tracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
    private val requestForeground: () -> Unit,
    private val requestBackground: () -> Unit,
) {
    val hasForeground: Boolean get() = foreground.value
    val hasBackground: Boolean get() = background.value

    /** Requests whichever of foreground/background access is still missing. */
    fun request() {
        if (!hasForeground) {
            requestForeground()
        } else if (!hasBackground) {
            requestBackground()
        }
    }
}

@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current

    fun isGranted(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(context, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

    val backgroundNotRequired = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val hasForeground = remember { mutableStateOf(isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    val hasBackground =
        remember {
            mutableStateOf(backgroundNotRequired || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }

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

    return remember {
        LocationPermissionState(
            foreground = hasForeground,
            background = hasBackground,
            requestForeground = {
                foregroundLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            requestBackground = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
        )
    }
}
