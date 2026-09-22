package com.github.tomasbjerre.wisp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.ui.Formatting

private const val FEEDBACK_URL = "https://github.com/tomasbjerre/wisp/issues"

/** See specs/ui-flows.md#1-home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: SessionRepository,
    onStart: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeViewModel(repository) } })
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wisp") },
                actions = {
                    // See specs/ui-flows.md#feedback-and-support.
                    IconButton(onClick = { uriHandler.openUri(FEEDBACK_URL) }) {
                        Icon(Icons.Filled.Info, contentDescription = "Feedback, problems, or feature requests")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No activities yet — tap Start to record your first route.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(session, onClick = { onOpenSession(session.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: Session,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(Formatting.dateTime(session.startedAt), style = MaterialTheme.typography.titleMedium)
            Text(
                "${Formatting.distance(session.distanceMeters)} · " +
                    "${Formatting.duration(session.durationSeconds)} · " +
                    Formatting.speedKmh(session.averageSpeedMps),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
