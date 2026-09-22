package com.github.tomasbjerre.wisp.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.RouteMap

/** See specs/ui-flows.md#2-tracking-active-recording. */
@Composable
fun TrackingScreen(onStopped: (sessionId: Long) -> Unit) {
    val context = LocalContext.current
    val permissions = rememberLocationPermissionState()
    val state by TrackingService.state.collectAsStateWithLifecycle()

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

    LaunchedEffect(state.isRecording, state.sessionId) {
        val id = state.sessionId
        if (hasRequestedStart && !state.isRecording && id != null) onStopped(id)
    }

    if (!permissions.hasForeground) {
        MissingPermission(onGrant = permissions::request)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RouteMap(route = state.route, modifier = Modifier.fillMaxSize().weight(1f))

        Surface(tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (!permissions.hasBackground) {
                    Text(
                        "Recording may stop if you leave the app — background location isn't granted.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(Formatting.speedKmh(state.currentSpeedMps), style = MaterialTheme.typography.displaySmall)
                Text(
                    "${Formatting.distance(state.distanceMeters)} · ${Formatting.duration(state.elapsedSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                TrackingControls(isPaused = state.isPaused)
            }
        }
    }
}

@Composable
private fun TrackingControls(isPaused: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { if (isPaused) TrackingService.resume(context) else TrackingService.pause(context) },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isPaused) "Resume" else "Pause")
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
private fun MissingPermission(onGrant: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Wisp needs location access to record your route.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant location access")
            }
        }
    }
}
