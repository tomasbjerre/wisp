package com.github.tomasbjerre.wisp.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem

/**
 * See specs/ui-flows.md#1a-home-settings. Reached from Home's gear icon, over that screen: the
 * one place the Metric/Imperial choice (specs/units.md) is configured, since it's a rarely
 * changed, app-wide preference rather than something tied to a live recording (unlike
 * [com.github.tomasbjerre.wisp.ui.tracking.SettingsScreen], reached from Tracking).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsScreen(
    unitPreferences: UnitPreferences,
    onBack: () -> Unit,
) {
    val unit by unitPreferences.unit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            UnitSystemToggle(unit = unit, onUnitChange = unitPreferences::setUnit)
        }
    }
}

/** See specs/units.md: a Metric/Imperial choice, the one setting on Home settings. */
@Composable
private fun UnitSystemToggle(
    unit: UnitSystem,
    onUnitChange: (UnitSystem) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
