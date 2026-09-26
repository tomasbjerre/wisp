package com.github.tomasbjerre.wisp.ui.tracking

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.location.TrackingUiState
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.MapType
import com.github.tomasbjerre.wisp.ui.common.MapTypeToggle
import com.github.tomasbjerre.wisp.ui.common.RouteMap

/** See specs/ui-flows.md#2-tracking-active-recording. */
@Composable
fun TrackingScreen(
    unitPreferences: UnitPreferences,
    onStopped: (sessionId: Long) -> Unit,
    onCancelled: () -> Unit,
    onOpenVoiceFeedbackSettings: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = rememberLocationPermissionState()
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
            // See specs/ui-flows.md#2a-voice-feedback-settings and specs/voice-feedback.md.
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = CircleShape,
                tonalElevation = 4.dp,
            ) {
                IconButton(onClick = onOpenVoiceFeedbackSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Voice feedback settings")
                }
            }
        }
        TrackingStatsPanel(state = state, permissions = permissions, unit = unit)
    }
}

@Composable
private fun TrackingStatsPanel(
    state: TrackingUiState,
    permissions: LocationPermissionState,
    unit: UnitSystem,
) {
    Surface(tonalElevation = 4.dp) {
        // navigationBarsPadding: MainActivity draws edge-to-edge, so without this
        // Pause/Stop end up underneath the system nav bar (3-button or gesture).
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
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
            if (state.isWaitingForMovement) {
                Text(
                    "Start moving to begin recording — it starts automatically once " +
                        "you're moving at a walking pace or faster.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
            TrackingControls(isPaused = state.isPaused, isWaitingForMovement = state.isWaitingForMovement)
        }
    }
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
        // Nothing to pause yet while waiting for movement — see specs/tracking.md#start-gating.
        if (!isWaitingForMovement) {
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
