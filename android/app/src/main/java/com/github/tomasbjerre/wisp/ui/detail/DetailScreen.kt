package com.github.tomasbjerre.wisp.ui.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
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
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.export.ActivityImageExporter
import com.github.tomasbjerre.wisp.export.CsvExporter
import com.github.tomasbjerre.wisp.export.CsvShareIntent
import com.github.tomasbjerre.wisp.export.ExportFileNames
import com.github.tomasbjerre.wisp.export.ImageShareIntent
import com.github.tomasbjerre.wisp.export.TrackPointCsvExporter
import com.github.tomasbjerre.wisp.location.LatLon
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.MapType
import com.github.tomasbjerre.wisp.ui.common.MapTypeToggle
import com.github.tomasbjerre.wisp.ui.common.RouteMap
import com.github.tomasbjerre.wisp.ui.splits.KmSplitRows
import org.osmdroid.views.MapView

/** See specs/ui-flows.md#3-detail-a-past-or-just-finished-session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repository: SessionRepository,
    sessionId: Long,
    unitPreferences: UnitPreferences,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onOpenKmSplits: () -> Unit,
) {
    val unit by unitPreferences.unit.collectAsStateWithLifecycle()
    val viewModel: DetailViewModel =
        viewModel(
            factory = viewModelFactory { initializer { DetailViewModel(repository, sessionId, unit) } },
        )
    val session by viewModel.session.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val kmSplitsSeconds by viewModel.kmSplitsSeconds.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // Only lasts for this viewing of the screen, same as Tracking's toggle — no settings
    // to persist it to. See specs/ui-flows.md#3-detail.
    var mapType by remember { mutableStateOf(MapType.STANDARD) }
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
            DetailMap(
                route = route,
                mapType = mapType,
                onMapTypeChange = { mapType = it },
                onMapViewReady = { mapView = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            SessionSummaryPanel(
                session = session,
                kmSplitsSeconds = kmSplitsSeconds,
                unit = unit,
                onOpenKmSplits = onOpenKmSplits,
                onBack = onBack,
                onExportImage = {
                    val map = mapView
                    val current = session
                    if (map != null && current != null) exportSessionAsImage(context, map, current, unit)
                },
                // See specs/export.md#single-activity-as-csv: same two-file format as the
                // full history export, scoped to just this one session.
                onExportCsv = { session?.let { exportSessionAsCsv(context, it, points) } },
                onDeleteClick = { showDeleteConfirm = true },
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onConfirm = { viewModel.delete(onDeleted) },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this activity?") },
        text = { Text("This can't be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** See specs/ui-flows.md#3-detail — Detail's map plus its standard/satellite toggle. */
@Composable
private fun DetailMap(
    route: List<LatLon>,
    mapType: MapType,
    onMapTypeChange: (MapType) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        RouteMap(
            route = route,
            mapType = mapType,
            modifier = Modifier.fillMaxSize(),
            onMapViewReady = onMapViewReady,
        )
        // No statusBarsPadding here, unlike Tracking's: this Box sits inside Scaffold's
        // content slot, below its topBar, which already accounts for the status bar inset.
        MapTypeToggle(
            mapType = mapType,
            onToggle = { onMapTypeChange(if (mapType == MapType.STANDARD) MapType.SATELLITE else MapType.STANDARD) },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
    }
}

@Composable
private fun SessionSummaryPanel(
    session: Session?,
    kmSplitsSeconds: List<Long>,
    unit: UnitSystem,
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
        Text(distanceAndDurationLine(session, unit), style = MaterialTheme.typography.titleLarge)
        Text(speedsLine(session, unit), style = MaterialTheme.typography.bodyLarge)
        // See specs/tracking.md#step-count. Omitted entirely with no step count, not just
        // empty — most sessions on most devices will never have one.
        if (session != null && session.steps > 0) {
            val stepsPerMinute = Formatting.stepsPerMinute(session.steps, session.durationSeconds)
            Text(stepsPerMinute, style = MaterialTheme.typography.bodyLarge)
        }
        // See specs/heart-rate.md#display. Omitted entirely with no reading, like steps.
        session?.maxHeartRateBpm?.let {
            Text("Max heart rate: ${Formatting.heartRate(it)}", style = MaterialTheme.typography.bodyLarge)
        }
        // See specs/tracking.md#km-splits. Omitted entirely with no complete km at all,
        // same reasoning as the steps line above.
        paceLine(kmSplitsSeconds, unit)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        // See specs/ui-flows.md#4-km-splits: the splits themselves live on their own
        // view (#86) — a list squeezed in here left room for only a few rows, and took
        // that room from the map. Omitted entirely under 1 km, not just disabled.
        if (kmSplitsSeconds.isNotEmpty()) {
            TextButton(
                onClick = onOpenKmSplits,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("${splitsLabel(unit)} (${kmSplitsSeconds.size})")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        // Two rows of two, not one row of four (see #60) — four equal-weight buttons in
        // one row left barely enough width each for "Export Image"/"Export CSV" to fit
        // without wrapping into an unreadable stack on a narrow phone. FilledTonalButton,
        // not OutlinedButton: a visible fill reads as a button against the map behind it,
        // where a thin outline alone did not.
        // Export actions on top, navigation/destructive below (see #112): the two
        // buttons someone taps repeatedly while reviewing an activity sit together,
        // above the two taps that leave the screen either way (back to the list, or
        // gone for good).
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // See specs/export.md#single-activity-as-csv.
                FilledTonalButton(onClick = onExportCsv, enabled = session != null, modifier = Modifier.weight(1f)) {
                    Text("Export CSV")
                }
                // See specs/export.md#single-activity-as-an-image.
                FilledTonalButton(onClick = onExportImage, enabled = session != null, modifier = Modifier.weight(1f)) {
                    Text("Export Image")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back")
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
    unit: UnitSystem,
) {
    val image = ActivityImageExporter.compose(mapView, session, unit)
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

private fun distanceAndDurationLine(
    session: Session?,
    unit: UnitSystem,
): String =
    session?.let {
        "${Formatting.distance(it.distanceMeters, unit)} · ${Formatting.duration(it.durationSeconds)}"
    } ?: ""

private fun speedsLine(
    session: Session?,
    unit: UnitSystem,
): String =
    session?.let {
        "Avg ${Formatting.speed(it.averageSpeedMps, unit)} · Max ${Formatting.speed(it.maxSpeedMps, unit)}"
    } ?: ""

/** See specs/tracking.md#km-splits. Null with no complete split at all — nothing to say. */
private fun paceLine(
    kmSplitsSeconds: List<Long>,
    unit: UnitSystem,
): String? {
    val average = KmSplitRows.averageSeconds(kmSplitsSeconds) ?: return null
    val fastest = KmSplitRows.fastestSeconds(kmSplitsSeconds)
    val fastestPart = fastest?.let { " · Fastest ${Formatting.pace(it, unit)}" } ?: ""
    return "Avg ${Formatting.pace(average, unit)}$fastestPart"
}

/** See specs/units.md: the Km splits view/link relabels to "Mile splits" under imperial. */
private fun splitsLabel(unit: UnitSystem): String = if (unit == UnitSystem.METRIC) "Km splits" else "Mile splits"
