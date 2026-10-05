package com.github.tomasbjerre.wisp.ui.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Whether the device's location/GPS setting is on — see
 * specs/tracking.md#location-services-off. Separate from location *permission*
 * ([LocationPermissionState]): a user can have granted permission with this off, and vice
 * versa.
 */
class LocationServicesState internal constructor(
    private val enabled: MutableState<Boolean>,
    private val openSettingsAction: () -> Unit,
) {
    val isEnabled: Boolean get() = enabled.value

    fun openSettings() = openSettingsAction()
}

@Composable
fun rememberLocationServicesState(): LocationServicesState {
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }

    fun isLocationEnabled() = LocationManagerCompat.isLocationEnabled(locationManager)

    val isEnabled = remember { mutableStateOf(isLocationEnabled()) }

    // A BroadcastReceiver, not LifecycleEventEffect(ON_RESUME): like Bluetooth (see
    // rememberHeartRateAvailable in SettingsScreen.kt), location can be toggled from Quick
    // Settings without the activity ever pausing.
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    isEnabled.value = isLocationEnabled()
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isEnabled.value = isLocationEnabled()
        onDispose { context.unregisterReceiver(receiver) }
    }

    return remember {
        LocationServicesState(
            enabled = isEnabled,
            openSettingsAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        )
    }
}
