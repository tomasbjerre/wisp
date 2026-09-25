package com.github.tomasbjerre.wisp.ui.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.export.ActivityImageExporter
import com.github.tomasbjerre.wisp.export.CsvExporter
import com.github.tomasbjerre.wisp.export.CsvShareIntent
import com.github.tomasbjerre.wisp.export.ExportFileNames
import com.github.tomasbjerre.wisp.export.ImageShareIntent
import com.github.tomasbjerre.wisp.export.TrackPointCsvExporter
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
    onOpenKmSplits: () -> Unit,
) {
    val viewModel: DetailViewModel =
        viewModel(factory = viewModelFactory { initializer { DetailViewModel(repository, sessionId) } })
    val session by viewModel.session.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val kmSplitsSeconds by viewModel.kmSplitsSeconds.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
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
                kmSplitCount = kmSplitsSeconds.size,
                onOpenKmSplits = onOpenKmSplits,
                onBack = onBack,
                onExportImage = {
                    val map = mapView
                    val current = session
                    if (map != null && current != null) exportSessionAsImage(context, map, current)
                },
                // See specs/export.md#single-activity-as-csv: same two-file format as the
                // full history export, scoped to just this one session.
                onExportCsv = { session?.let { exportSessionAsCsv(context, it, points) } },
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
    kmSplitCount: Int,
    onOpenKmSplits: () -> Unit,
    onBack: () -> Unit,
    onExportImage: () -> Unit,
    onExportCsv: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Both lines always render, even before `session` loads (it's null for a frame or
        // two while its Flow's first value is still in flight) — otherwise the button row
        // below visibly jumps down once the text pops in. See specs/ui-flows.md#3-detail.
        Text(distanceAndDurationLine(session), style = MaterialTheme.typography.titleLarge)
        Text(speedsLine(session), style = MaterialTheme.typography.bodyLarge)
        // See specs/tracking.md#step-count. Omitted entirely with no step count, not just
        // empty — most sessions on most devices will never have one.
        if (session != null && session.steps > 0) {
            val stepsPerMinute = Formatting.stepsPerMinute(session.steps, session.durationSeconds)
            Text(stepsPerMinute, style = MaterialTheme.typography.bodyLarge)
        }
        // See specs/ui-flows.md#4-km-splits: the splits themselves live on their own
        // view (#86) — a list squeezed in here left room for only a few rows, and took
        // that room from the map. Omitted entirely under 1 km, not just disabled.
        if (kmSplitCount > 0) {
            TextButton(
                onClick = onOpenKmSplits,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Km splits ($kmSplitCount)")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        // Two rows of two, not one row of four (see #60) — four equal-weight buttons in
        // one row left barely enough width each for "Export Image"/"Export CSV" to fit
        // without wrapping into an unreadable stack on a narrow phone. FilledTonalButton,
        // not OutlinedButton: a visible fill reads as a button against the map behind it,
        // where a thin outline alone did not.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
                // See specs/export.md#single-activity-as-an-image.
                FilledTonalButton(onClick = onExportImage, enabled = session != null, modifier = Modifier.weight(1f)) {
                    Text("Export Image")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // See specs/export.md#single-activity-as-csv.
                FilledTonalButton(onClick = onExportCsv, enabled = session != null, modifier = Modifier.weight(1f)) {
                    Text("Export CSV")
                }
                FilledTonalButton(onClick = onDeleteClick, modifier = Modifier.weight(1f)) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun exportSessionAsImage(
    context: Context,
    mapView: MapView,
    session: Session,
) {
    val image = ActivityImageExporter.compose(mapView, session)
    context.startActivity(ImageShareIntent.build(context, image, ExportFileNames.activityImage(session.startedAt)))
}

private fun exportSessionAsCsv(
    context: Context,
    session: Session,
    points: List<TrackPoint>,
) {
    val sessionsCsv = CsvExporter.toCsv(listOf(session))
    val trackPointsCsv = TrackPointCsvExporter.toCsv(listOf(session to points))
    context.startActivity(
        CsvShareIntent.build(
            context,
            sessionsCsv,
            trackPointsCsv,
            fileNames = ExportFileNames.activityCsv(session.startedAt),
            chooserTitle = "Export activity as CSV",
        ),
    )
}

private fun distanceAndDurationLine(session: Session?): String =
    session?.let { "${Formatting.distance(it.distanceMeters)} · ${Formatting.duration(it.durationSeconds)}" } ?: ""

private fun speedsLine(session: Session?): String =
    session?.let { "Avg ${Formatting.speedKmh(it.averageSpeedMps)} · Max ${Formatting.speedKmh(it.maxSpeedMps)}" } ?: ""
