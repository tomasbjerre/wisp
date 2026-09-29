package com.github.tomasbjerre.wisp.ui.detail

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.export.ActivityImageExporter
import com.github.tomasbjerre.wisp.export.CsvDeviceWriter
import com.github.tomasbjerre.wisp.export.CsvExporter
import com.github.tomasbjerre.wisp.export.CsvShareIntent
import com.github.tomasbjerre.wisp.export.ExportFileNames
import com.github.tomasbjerre.wisp.export.ImageDeviceWriter
import com.github.tomasbjerre.wisp.export.ImageShareIntent
import com.github.tomasbjerre.wisp.export.TrackPointCsvExporter
import com.github.tomasbjerre.wisp.location.LatLon
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.common.MapType
import com.github.tomasbjerre.wisp.ui.common.MapTypeToggle
import com.github.tomasbjerre.wisp.ui.common.RouteMap
import com.github.tomasbjerre.wisp.ui.splits.KmSplitRows
import com.github.tomasbjerre.wisp.util.GeoUtils
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
    val kmSplits by viewModel.kmSplits.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // Only lasts for this viewing of the screen, same as Tracking's toggle — no settings
    // to persist it to. See specs/ui-flows.md#3-detail.
    var mapType by remember { mutableStateOf(MapType.STANDARD) }
    val context = LocalContext.current
    val exportActions = rememberExportActions(context, session, points, mapView, unit)

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
                // See specs/export.md#trigger: one action offering both formats, the same
                // single-export-entry-point pattern Home uses (see #173) — rather than a
                // separate button per format taking up the button row below.
                actions = { ExportButton(exportActions, enabled = session != null) },
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
                kmSplits = kmSplits,
                unit = unit,
                onOpenKmSplits = onOpenKmSplits,
                onBack = onBack,
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

/** Stats a session may not have — each omitted entirely rather than shown empty. */
@Composable
private fun OptionalStatLines(session: Session?) {
    // See specs/calories.md#where-it-is-shown: displayed here, chosen on Tracking.
    ActivityType.fromId(session?.activityType)?.let {
        Text("Activity: ${it.label}", style = MaterialTheme.typography.bodyLarge)
    }
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
    // See specs/calories.md#where-it-is-shown. Omitted entirely when the session has none.
    session?.kilocalories()?.let {
        Text("Calories: ${Formatting.calories(it)}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionSummaryPanel(
    session: Session?,
    kmSplits: GeoUtils.KmSplits,
    unit: UnitSystem,
    onOpenKmSplits: () -> Unit,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Both lines always render, even before `session` loads (it's null for a frame or
        // two while its Flow's first value is still in flight) — otherwise the button row
        // below visibly jumps down once the text pops in. See specs/ui-flows.md#3-detail.
        Text(distanceAndDurationLine(session, unit), style = MaterialTheme.typography.titleLarge)
        Text(speedsLine(session, unit), style = MaterialTheme.typography.bodyLarge)
        OptionalStatLines(session)
        // See specs/tracking.md#km-splits. Omitted entirely with no complete km at all,
        // same reasoning as the steps line above.
        paceLine(kmSplits.completeSeconds, unit)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        // See specs/ui-flows.md#4-km-splits: the splits themselves live on their own
        // view (#86) — a list squeezed in here left room for only a few rows, and took
        // that room from the map. Omitted entirely with nothing to list, not just disabled —
        // a partial split alone is enough (#149: under a mile, imperial has no complete one).
        KmSplitRows.linkLabel(kmSplits, unit)?.let { linkLabel ->
            TextButton(
                onClick = onOpenKmSplits,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(linkLabel)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        // See #173: export now lives in the top bar (see ExportButton), leaving just
        // this one row — FilledTonalButton, not OutlinedButton: a visible fill reads as
        // a button against the map behind it, where a thin outline alone did not.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            FilledTonalButton(onClick = onDeleteClick, modifier = Modifier.weight(1f)) {
                Text("Delete")
            }
        }
    }
}

/**
 * See specs/export.md#trigger: one icon in the top bar offering both export formats — the
 * same single-export-entry-point pattern ExportMenu/Home uses, extended with a CSV/Image
 * choice first since Detail (unlike Home) has two formats to offer. Disabled until the
 * session has loaded, same as the two export buttons this replaced (see #173).
 */
@Composable
private fun ExportButton(
    exportActions: ExportActions,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.Share, contentDescription = "Export this activity")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExportFormatGroup(
                label = "CSV",
                onShare = exportActions.onShareCsv,
                onSaveToDevice = exportActions.onSaveCsvToDevice,
                onDismiss = { expanded = false },
            )
            HorizontalDivider()
            ExportFormatGroup(
                label = "Image",
                onShare = exportActions.onShareImage,
                onSaveToDevice = exportActions.onSaveImageToDevice,
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun ExportFormatGroup(
    label: String,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    DropdownMenuItem(
        text = { Text("Share") },
        onClick = {
            onDismiss()
            onShare()
        },
    )
    DropdownMenuItem(
        text = { Text("Save to device") },
        onClick = {
            onDismiss()
            onSaveToDevice()
        },
    )
}

/** See specs/export.md#trigger — a **Share** and a **Save to device** action for each export. */
private data class ExportActions(
    val onShareCsv: () -> Unit,
    val onSaveCsvToDevice: () -> Unit,
    val onShareImage: () -> Unit,
    val onSaveImageToDevice: () -> Unit,
)

/**
 * See specs/export.md#trigger and issue #141: **Save to device** writes directly to a
 * location the user picks via the Storage Access Framework, instead of only ever
 * handing the file to another app via the share sheet ([exportSessionAsCsv]/
 * [exportSessionAsImage]). Content is built ahead of the picker — it can't be suspended
 * for it — and written once a location is actually chosen (the callback below).
 */
@Composable
private fun rememberExportActions(
    context: Context,
    session: Session?,
    points: List<TrackPoint>,
    mapView: MapView?,
    unit: UnitSystem,
): ExportActions {
    var pendingCsv by remember { mutableStateOf<Pair<ExportFileNames.CsvPair, Pair<String, String>>?>(null) }
    val saveCsvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            val pending = pendingCsv
            if (treeUri != null && pending != null) {
                val (fileNames, csv) = pending
                CsvDeviceWriter.write(context, treeUri, fileNames, csv.first, csv.second)
            }
            pendingCsv = null
        }
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
    val saveImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val bitmap = pendingImage
            if (uri != null && bitmap != null) ImageDeviceWriter.write(context, uri, bitmap)
            pendingImage = null
        }

    return ExportActions(
        onShareCsv = { session?.let { exportSessionAsCsv(context, it, points) } },
        onSaveCsvToDevice = {
            session?.let {
                val sessionsCsv = CsvExporter.toCsv(listOf(it))
                val trackPointsCsv = TrackPointCsvExporter.toCsv(listOf(it to points))
                pendingCsv = ExportFileNames.activityCsv(it.startedAt) to (sessionsCsv to trackPointsCsv)
                saveCsvLauncher.launch(null)
            }
        },
        onShareImage = {
            val map = mapView
            val current = session
            if (map != null && current != null) exportSessionAsImage(context, map, current, unit)
        },
        onSaveImageToDevice = {
            val map = mapView
            val current = session
            if (map != null && current != null) {
                pendingImage = ActivityImageExporter.compose(map, current, unit)
                saveImageLauncher.launch(ExportFileNames.activityImage(current.startedAt))
            }
        },
    )
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
