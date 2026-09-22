package com.github.tomasbjerre.wisp.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.RouteMap

/** See specs/ui-flows.md#3-detail-a-past-or-just-finished-session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repository: SessionRepository,
    sessionId: Long,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: DetailViewModel =
        viewModel(factory = viewModelFactory { initializer { DetailViewModel(repository, sessionId) } })
    val session by viewModel.session.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.let { Formatting.dateTime(it.startedAt) } ?: "") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RouteMap(route = route, modifier = Modifier.fillMaxSize().weight(1f))

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val s = session
                if (s != null) {
                    Text(
                        "${Formatting.distance(s.distanceMeters)} · ${Formatting.duration(s.durationSeconds)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Avg ${Formatting.speedKmh(s.averageSpeedMps)} · Max ${Formatting.speedKmh(s.maxSpeedMps)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text("Delete")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this activity?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(onDeleted) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
