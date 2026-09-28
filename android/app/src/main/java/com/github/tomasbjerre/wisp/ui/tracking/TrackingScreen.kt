package com.github.tomasbjerre.wisp.ui.tracking

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.HeartRatePreferences
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.location.HeartRateMonitor
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.location.TrackingUiState
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.TestTags
import com.github.tomasbjerre.wisp.ui.common.ActivityTypeChoice
import com.github.tomasbjerre.wisp.ui.common.MapType
import com.github.tomasbjerre.wisp.ui.common.MapTypeToggle
import com.github.tomasbjerre.wisp.ui.common.RouteMap

/** See specs/ui-flows.md#2-tracking-active-recording. */
@Composable
fun TrackingScreen(
    unitPreferences: UnitPreferences,
    heartRatePreferences: HeartRatePreferences,
    onStopped: (sessionId: Long) -> Unit,
    onCancelled: () -> Unit,
    onOpenVoiceFeedbackSettings: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = rememberLocationPermissionState()
    val notifications = rememberNotificationState(askOnEntry = permissions.hasForeground)
    val state by TrackingService.state.collectAsStateWithLifecycle()
    val unit by unitPreferences.unit.collectAsStateWithLifecycle()

    // See specs/ui-flows.md#2-tracking-active-recording: back behaves exactly like
    // tapping Stop (below), not like leaving the screen — without this, system/gesture
    // back would pop straight to Home while the session is still active, orphaning it —
    // TrackingService keeps running against a sessionId no longer reachable from the
    // UI, and a later Start creates a second session on top of it instead of resuming
    // or stopping the first. The existing LaunchedEffects below (watching isRecording/
    // sessionId/wasDiscarded) already navigate correctly once TrackingService.stop
    // updates state, the same as if Stop itself had been tapped.
    BackHandler(enabled = state.isRecording) { TrackingService.stop(context) }

    // Start exactly once per time this screen is entered — not reactively on every
    // isRecording flip, otherwise stopping (isRecording -> false) would immediately
    // start a fresh session again right as we navigate away.
    var hasRequestedStart by remember { mutableStateOf(false) }
    // Skipped when a session is already recording: navigating to voice feedback settings
    // and back re-enters this screen with a fresh hasRequestedStart, and starting again
    // would replace the running session with a new one.
    LaunchedEffect(permissions.hasForeground) {
        if (permissions.hasForeground && !hasRequestedStart && !state.isRecording) {
            hasRequestedStart = true
            TrackingService.start(context)
        }
    }

    // TrackingService.start() is async, so right after entering this screen, `state` can
    // still briefly be the previous session's leftover "stopped" state (isRecording =
    // false, sessionId = the OLD session) — react to a stop only once this screen has
    // actually observed its own session start, or a second-in-a-row recording bounces
    // straight back to the previous session's Detail screen instead of starting.
    var hasStartedRecording by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRecording) {
        if (state.isRecording) hasStartedRecording = true
    }

    LaunchedEffect(state.isRecording, state.sessionId) {
        val id = state.sessionId
        if (hasStartedRecording && !state.isRecording && id != null) onStopped(id)
    }

    // See specs/tracking.md#start-gating: Stop before any movement discards the session
    // (sessionId is cleared) instead of finishing it — nothing to navigate to for that.
    LaunchedEffect(state.wasDiscarded) {
        if (hasStartedRecording && state.wasDiscarded) onCancelled()
    }

    if (!permissions.hasForeground) {
        MissingPermission(onGrant = permissions::request)
        return
    }

    // See specs/tracking.md#start-gating: no map at all until a real position is known,
    // rather than showing a map with nothing to center on (see RouteMap's fallback).
    if (state.isLocating) {
        LocatingState()
        return
    }

    // See specs/ui-flows.md#2-tracking-active-recording: only lasts for this viewing of
    // the screen, not remembered between recordings — no settings to persist it to.
    var mapType by remember { mutableStateOf(MapType.STANDARD) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            RouteMap(route = state.route, isLive = true, mapType = mapType, modifier = Modifier.fillMaxSize())
            MapTypeToggle(
                mapType = mapType,
                onToggle = { mapType = if (mapType == MapType.STANDARD) MapType.SATELLITE else MapType.STANDARD },
                // statusBarsPadding: MainActivity draws edge-to-edge (see
                // TrackingStatsPanel's navigationBarsPadding below for the same reason
                // at the bottom), so without this the toggle sits under the status bar
                // — easy to miss or to hit the notification shade instead (see #55).
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            )
            VoiceFeedbackSettingsButton(
                onClick = onOpenVoiceFeedbackSettings,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            )
        }
        TrackingStatsPanel(
            state = state,
            permissions = permissions,
            notifications = notifications,
            unit = unit,
            heartRatePreferences = heartRatePreferences,
        )
    }
}

/** See specs/ui-flows.md#2a-voice-feedback-settings and specs/voice-feedback.md. */
@Composable
private fun VoiceFeedbackSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = CircleShape, tonalElevation = 4.dp) {
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.Settings, contentDescription = "Voice feedback settings")
        }
    }
}

@Composable
private fun TrackingStatsPanel(
    state: TrackingUiState,
    permissions: LocationPermissionState,
    notifications: NotificationState,
    unit: UnitSystem,
    heartRatePreferences: HeartRatePreferences,
) {
    Surface(tonalElevation = 4.dp) {
        // navigationBarsPadding: MainActivity draws edge-to-edge, so without this
        // Pause/Stop end up underneath the system nav bar (3-button or gesture).
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            PermissionAdvisories(permissions, notifications)
            if (state.isWaitingForMovement) {
                Text(
                    "Start moving to begin recording — it starts automatically once " +
                        "you're moving at a walking pace or faster. You can also force " +
                        "start below if you'd rather begin recording immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TrackingStatLines(state, unit)
            ActivityTypeSection(state)
            HeartRateSection(state, heartRatePreferences)
            TrackingControls(isPaused = state.isPaused, isWaitingForMovement = state.isWaitingForMovement)
        }
    }
}

/** Advisory lines, none blocking — see specs/ui-flows.md#2-tracking-active-recording. */
@Composable
private fun PermissionAdvisories(
    permissions: LocationPermissionState,
    notifications: NotificationState,
) {
    if (!permissions.hasBackground) {
        Text(
            "Recording may stop if you leave the app — background location isn't granted.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (!permissions.hasBatteryExemption) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Battery optimization may pause recording in the background.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = permissions::requestBatteryExemption) {
                Text("Fix")
            }
        }
    }
    if (!notifications.isAllowed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off — you won't see the recording when you swipe down.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = notifications::openSettings) {
                Text("Fix")
            }
        }
    }
}

@Composable
private fun TrackingStatLines(
    state: TrackingUiState,
    unit: UnitSystem,
) {
    Text(Formatting.speed(state.currentSpeedMps, unit), style = MaterialTheme.typography.displaySmall)
    Text(
        "${Formatting.distance(state.distanceMeters, unit)} · ${Formatting.duration(state.elapsedSeconds)}",
        style = MaterialTheme.typography.bodyLarge,
    )
    // See specs/tracking.md#step-count: omitted entirely with no step count, same
    // rule as Detail — most sessions on most devices will never have one.
    if (state.steps > 0) {
        Text(
            Formatting.stepsPerMinute(state.steps, state.elapsedSeconds),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    KmSplitLines(state, unit)
    // See specs/calories.md#where-it-is-shown: omitted when the session has none.
    state.kilocalories?.let {
        Text("Calories: ${Formatting.calories(it)}", style = MaterialTheme.typography.bodyLarge)
    }
}

/** See specs/calories.md#activity-type: chosen here, in any state, and remembered for next time. */
@Composable
private fun ActivityTypeSection(state: TrackingUiState) {
    val context = LocalContext.current
    ActivityTypeChoice(
        selected = state.activityType,
        onSelect = { TrackingService.setActivityType(context, it) },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun KmSplitLines(
    state: TrackingUiState,
    unit: UnitSystem,
) {
    // See specs/tracking.md#km-splits: only the latest split, not the full list
    // Detail shows — there's no room for a growing list on this screen, and
    // "how was that last km" is what's actually useful mid-run.
    state.latestKmSplitSeconds?.let { seconds ->
        Text(
            "Last ${unit.distanceWordSingular}: ${Formatting.duration(seconds)}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    // See specs/tracking.md#km-splits: null until a second complete split exists
    // to compare against, same threshold as Detail/Km splits' fastest/slowest.
    state.fastestKmSplitSeconds?.let { seconds ->
        Text(
            "Fastest ${unit.distanceWordSingular}: ${Formatting.duration(seconds)}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The heart rate line and its switch — see specs/heart-rate.md#setting and #display. */
@Composable
private fun HeartRateSection(
    state: TrackingUiState,
    heartRatePreferences: HeartRatePreferences,
) {
    val enabled by heartRatePreferences.enabled.collectAsStateWithLifecycle()
    val available = rememberHeartRateAvailable()

    // With nothing to connect to, the setting is switched off (and persisted as off)
    // rather than left on for a monitor that can't be reached.
    LaunchedEffect(available, enabled) {
        if (!available && enabled) heartRatePreferences.setEnabled(false)
    }

    // Omitted with the setting off; a placeholder, not hidden, while on but without a
    // current reading, so it's clear Wisp is still looking for a monitor.
    if (enabled) HeartRateLine(state)
    HeartRateSwitch(enabled = enabled, available = available, onEnabledChange = heartRatePreferences::setEnabled)
}

/** Whether Bluetooth can currently reach a monitor, kept current as it's turned on/off. */
@Composable
private fun rememberHeartRateAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(HeartRateMonitor.isAvailable(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    available = HeartRateMonitor.isAvailable(context)
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        available = HeartRateMonitor.isAvailable(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return available
}

/**
 * See specs/heart-rate.md#setting. Turning it on first asks for the Bluetooth access it
 * needs (Android 12+ only — earlier versions have it at install time); declined leaves it
 * off. Disabled while there's no Bluetooth to reach a monitor with.
 */
@Composable
private fun HeartRateSwitch(
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) onEnabledChange(true)
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (available) "Heart rate monitor" else "Heart rate monitor (Bluetooth is off)",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = enabled && available,
            enabled = available,
            modifier = Modifier.testTag(TestTags.HEART_RATE_SWITCH),
            onCheckedChange = { wanted ->
                val missing =
                    if (wanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HeartRateMonitor.REQUIRED_PERMISSIONS_S.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                    } else {
                        emptyList()
                    }
                if (missing.isEmpty()) onEnabledChange(wanted) else launcher.launch(missing.toTypedArray())
            },
        )
    }
}

@Composable
private fun HeartRateLine(state: TrackingUiState) {
    val current = state.heartRateBpm?.let(Formatting::heartRate) ?: "—"
    val max = state.maxHeartRateBpm?.let { " · Max ${Formatting.heartRate(it)}" } ?: ""
    Text("Heart rate: $current$max", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun TrackingControls(
    isPaused: Boolean,
    isWaitingForMovement: Boolean,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Nothing to pause yet while waiting for movement — offer to skip the wait
        // instead, see specs/tracking.md#start-gating.
        if (isWaitingForMovement) {
            OutlinedButton(
                onClick = { TrackingService.forceStart(context) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Force start")
            }
        } else {
            OutlinedButton(
                onClick = { if (isPaused) TrackingService.resume(context) else TrackingService.pause(context) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isPaused) "Continue" else "Pause")
            }
        }
        Button(
            onClick = { TrackingService.stop(context) },
            modifier = Modifier.weight(1f),
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun LocatingState() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            Text("Finding your location…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MissingPermission(onGrant: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Wisp Tracker needs location access to record your route.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant location access")
            }
        }
    }
}
