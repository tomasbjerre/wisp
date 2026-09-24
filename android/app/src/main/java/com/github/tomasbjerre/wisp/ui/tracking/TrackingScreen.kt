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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.location.TrackingUiState
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.RouteMap

/** See specs/ui-flows.md#2-tracking-active-recording. */
@Composable
fun TrackingScreen(
    onStopped: (sessionId: Long) -> Unit,
    onCancelled: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = rememberLocationPermissionState()
    val state by TrackingService.state.collectAsStateWithLifecycle()

    // See specs/ui-flows.md#2-tracking-active-recording: the only way out of this screen
    // is Stop. Without this, system/gesture back would pop straight to Home while the
    // session is still active, orphaning it — TrackingService keeps running against a
    // sessionId no longer reachable from the UI, and a later Start creates a second
    // session on top of it instead of resuming or stopping the first.
    BackHandler(enabled = state.isRecording) {}

    // Start exactly once per time this screen is entered — not reactively on every
    // isRecording flip, otherwise stopping (isRecording -> false) would immediately
    // start a fresh session again right as we navigate away.
    var hasRequestedStart by remember { mutableStateOf(false) }
    LaunchedEffect(permissions.hasForeground) {
        if (permissions.hasForeground && !hasRequestedStart) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        RouteMap(route = state.route, isLive = true, modifier = Modifier.fillMaxSize().weight(1f))
        TrackingStatsPanel(state = state, permissions = permissions)
    }
}

@Composable
private fun TrackingStatsPanel(
    state: TrackingUiState,
    permissions: LocationPermissionState,
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
            Text(Formatting.speedKmh(state.currentSpeedMps), style = MaterialTheme.typography.displaySmall)
            Text(
                "${Formatting.distance(state.distanceMeters)} · ${Formatting.duration(state.elapsedSeconds)}",
                style = MaterialTheme.typography.bodyLarge,
            )
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
