package com.github.tomasbjerre.wisp.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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
import com.github.tomasbjerre.wisp.export.CsvExporter
import com.github.tomasbjerre.wisp.export.CsvShareIntent
import com.github.tomasbjerre.wisp.export.ExportFileNames
import com.github.tomasbjerre.wisp.export.TrackPointCsvExporter
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.TestTags
import kotlinx.coroutines.launch

private const val FEEDBACK_URL = "https://github.com/tomasbjerre/wisp/issues"

// GitHub-rendered link, same pattern as PRIVACY.md's Play Store listing URL (see
// android/README.md#one-time-setup) — not a raw file path, so it opens as a normal web
// page in the user's browser rather than downloading a markdown file.
private const val USER_MANUAL_URL = "https://github.com/tomasbjerre/wisp/blob/main/docs/user-manual.md"

/** See specs/ui-flows.md#1-home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: SessionRepository,
    unitPreferences: UnitPreferences,
    onStart: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(factory = viewModelFactory { initializer { HomeViewModel(repository) } })
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val unit by unitPreferences.unit.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Session?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HomeTopBar(
                sessions = sessions,
                loadPointsBySession = viewModel::loadPointsBySession,
                onInfoClick = { showInfo = true },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }

            // See specs/units.md and specs/ui-flows.md#1-home.
            UnitSystemToggle(
                unit = unit,
                onUnitChange = unitPreferences::setUnit,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No activities yet — tap Start to record your first route.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                SessionList(
                    sessions = sessions,
                    unit = unit,
                    onOpenSession = onOpenSession,
                    onDeleteClick = { pendingDelete = it },
                )
            }
        }
    }

    // See specs/ui-flows.md#1-home.
    pendingDelete?.let { session ->
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.delete(session)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // See specs/ui-flows.md#feedback-and-support.
    if (showInfo) {
        InformationDialog(onDismiss = { showInfo = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    sessions: List<Session>,
    loadPointsBySession: suspend () -> List<Pair<Session, List<TrackPoint>>>,
    onInfoClick: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    TopAppBar(
        title = { Text("Wisp Tracker") },
        actions = {
            // See specs/export.md#trigger and #history-as-csv.
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        val sessionsCsv = CsvExporter.toCsv(sessions)
                        val trackPointsCsv = TrackPointCsvExporter.toCsv(loadPointsBySession())
                        val fileNames = ExportFileNames.historyCsv(System.currentTimeMillis())
                        context.startActivity(CsvShareIntent.build(context, sessionsCsv, trackPointsCsv, fileNames))
                    }
                },
                enabled = sessions.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export history as CSV")
            }
            // See specs/ui-flows.md#feedback-and-support.
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Filled.Info, contentDescription = "App version, feedback, and the user manual")
            }
        },
    )
}

/** See specs/ui-flows.md#feedback-and-support. */
@Composable
private fun InformationDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wisp") },
        text = {
            Column {
                Text("Version ${appVersionName(context)}", style = MaterialTheme.typography.bodyLarge)
                // Same "<model>, Android <release>" shape as the bug report issue
                // template's own placeholder text, so this can be copied straight in.
                Text(deviceInfo(), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { uriHandler.openUri(FEEDBACK_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Report a problem or request a feature")
                }
                TextButton(onClick = { uriHandler.openUri(USER_MANUAL_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("User manual")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// Read from the installed package rather than a compile-time constant, so this always
// reflects what's actually running on the device, the same version Play Console and
// the device's own app-info screen would show.
private fun appVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

private fun deviceInfo(): String = "${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

/** See specs/units.md: a Metric/Imperial choice, the one setting Wisp has on Home. */
@Composable
private fun UnitSystemToggle(
    unit: UnitSystem,
    onUnitChange: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        UnitSystem.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = unit == option,
                onClick = { onUnitChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = UnitSystem.entries.size),
            ) {
                Text(if (option == UnitSystem.METRIC) "Metric" else "Imperial")
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<Session>,
    unit: UnitSystem,
    onOpenSession: (Long) -> Unit,
    onDeleteClick: (Session) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionRow(
                session,
                unit = unit,
                onClick = { onOpenSession(session.id) },
                onDeleteClick = { onDeleteClick(session) },
            )
        }
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
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: Session,
    unit: UnitSystem,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(TestTags.HISTORY_ROW)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                Text(
                    Formatting.dateTime(session.startedAt) + (session.nearestCity?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${Formatting.distance(session.distanceMeters, unit)} · " +
                        "${Formatting.duration(session.durationSeconds)} · " +
                        Formatting.speed(session.averageSpeedMps, unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this activity")
            }
        }
    }
}
