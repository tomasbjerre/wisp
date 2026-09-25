package com.github.tomasbjerre.wisp.ui.splits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.ui.Formatting

/** See specs/ui-flows.md#4-km-splits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmSplitsScreen(
    repository: SessionRepository,
    sessionId: Long,
    onBack: () -> Unit,
) {
    val viewModel: KmSplitsViewModel =
        viewModel(factory = viewModelFactory { initializer { KmSplitsViewModel(repository, sessionId) } })
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val rows = KmSplitRows.rows(splits)
    val extremes = KmSplitRows.extremes(splits)
    // See specs/ui-flows.md#4-km-splits: the whole column goes when there are no steps
    // per split, rather than a column of blanks or zeros.
    val showSteps = rows.any { it.steps != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Km splits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (extremes != null) {
                Text(
                    "Fastest: km ${extremes.fastestKm} · ${Formatting.duration(extremes.fastestSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Slowest: km ${extremes.slowestKm} · ${Formatting.duration(extremes.slowestSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            SplitRowLayout(
                km = { Text("Km", fontWeight = FontWeight.Bold) },
                time = { Text("Time", fontWeight = FontWeight.Bold, textAlign = TextAlign.End) },
                speed = { Text("Speed", fontWeight = FontWeight.Bold, textAlign = TextAlign.End) },
                steps =
                    if (showSteps) {
                        { Text("Steps", fontWeight = FontWeight.Bold, textAlign = TextAlign.End) }
                    } else {
                        null
                    },
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows) { row -> SplitRow(row, showSteps) }
            }
        }
    }
}

@Composable
private fun SplitRow(
    row: KmSplitRow,
    showSteps: Boolean,
) {
    // The partial km is set apart in a dimmer color: it's a shorter distance than
    // every other row, so its time isn't directly comparable to theirs (its speed is).
    val color = if (row.isPartial) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    SplitRowLayout(
        km = { Text(row.label, color = color) },
        time = { Text(Formatting.duration(row.durationSeconds), color = color, textAlign = TextAlign.End) },
        speed = { Text(Formatting.speedKmh(row.speedMps), color = color, textAlign = TextAlign.End) },
        steps =
            if (showSteps) {
                { Text(row.steps?.toString() ?: "", color = color, textAlign = TextAlign.End) }
            } else {
                null
            },
    )
}

@Composable
private fun SplitRowLayout(
    km: @Composable () -> Unit,
    time: @Composable () -> Unit,
    speed: @Composable () -> Unit,
    steps: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(KM_COLUMN_WIDTH)) { km() }
        Box(modifier = Modifier.width(TIME_COLUMN_WIDTH), contentAlignment = Alignment.CenterEnd) { time() }
        Box(modifier = Modifier.width(SPEED_COLUMN_WIDTH), contentAlignment = Alignment.CenterEnd) { speed() }
        if (steps != null) {
            Box(modifier = Modifier.weight(1f).padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                steps()
            }
        }
    }
}

private val KM_COLUMN_WIDTH = 48.dp
private val TIME_COLUMN_WIDTH = 56.dp
private val SPEED_COLUMN_WIDTH = 88.dp
