package com.github.tomasbjerre.wisp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.export.ActivityImageExporter
import com.github.tomasbjerre.wisp.export.ImageShareIntent
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.RouteMap
import org.osmdroid.views.MapView

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
    val kmSplitsSeconds by viewModel.kmSplitsSeconds.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session?.let {
                            Formatting.dateTime(it.startedAt) + (it.nearestCity?.let { city -> " · $city" } ?: "")
                        } ?: "",
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RouteMap(
                route = route,
                modifier = Modifier.fillMaxSize().weight(1f),
                onMapViewReady = { mapView = it },
            )

            SessionSummaryPanel(
                session = session,
                kmSplitsSeconds = kmSplitsSeconds,
                onBack = onBack,
                onExportImage = {
                    val map = mapView ?: return@SessionSummaryPanel
                    val current = session ?: return@SessionSummaryPanel
                    val image = ActivityImageExporter.compose(map, current)
                    context.startActivity(ImageShareIntent.build(context, image))
                },
                onDeleteClick = { showDeleteConfirm = true },
            )
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

@Composable
private fun SessionSummaryPanel(
    session: Session?,
    kmSplitsSeconds: List<Long>,
    onBack: () -> Unit,
    onExportImage: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Both lines always render, even before `session` loads (it's null for a frame or
        // two while its Flow's first value is still in flight) — otherwise the button row
        // below visibly jumps down once the text pops in. See specs/ui-flows.md#3-detail.
        Text(distanceAndDurationLine(session), style = MaterialTheme.typography.titleLarge)
        Text(speedsLine(session), style = MaterialTheme.typography.bodyLarge)
        // See specs/tracking.md#km-splits. Omitted entirely under 1 km, not just empty.
        if (kmSplitsSeconds.isNotEmpty()) {
            KmSplitsList(kmSplitsSeconds, modifier = Modifier.padding(top = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            // See specs/export.md#single-activity-as-an-image.
            OutlinedButton(onClick = onExportImage, enabled = session != null, modifier = Modifier.weight(1f)) {
                Text("Export Image")
            }
            OutlinedButton(onClick = onDeleteClick, modifier = Modifier.weight(1f)) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun KmSplitsList(
    kmSplitsSeconds: List<Long>,
    modifier: Modifier = Modifier,
) {
    // Bounded height + LazyColumn: a short run's few splits render with no scrolling at
    // all, while a long one (marathon-length) scrolls within this panel instead of
    // pushing the Back/Export/Delete row off the bottom of the screen.
    LazyColumn(modifier = modifier.fillMaxWidth().heightIn(max = 160.dp)) {
        items(kmSplitsSeconds.size) { index ->
            Text(
                "Km ${index + 1}: ${Formatting.duration(kmSplitsSeconds[index])}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun distanceAndDurationLine(session: Session?): String =
    session?.let { "${Formatting.distance(it.distanceMeters)} · ${Formatting.duration(it.durationSeconds)}" } ?: ""

private fun speedsLine(session: Session?): String =
    session?.let { "Avg ${Formatting.speedKmh(it.averageSpeedMps)} · Max ${Formatting.speedKmh(it.maxSpeedMps)}" } ?: ""
