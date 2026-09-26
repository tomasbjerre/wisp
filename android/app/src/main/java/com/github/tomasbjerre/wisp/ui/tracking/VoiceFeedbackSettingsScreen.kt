package com.github.tomasbjerre.wisp.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.VoiceFeedbackPreferences
import com.github.tomasbjerre.wisp.ui.TestTags

/** See specs/ui-flows.md#2a-voice-feedback-settings and specs/voice-feedback.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceFeedbackSettingsScreen(
    preferences: VoiceFeedbackPreferences,
    onBack: () -> Unit,
) {
    val settings by preferences.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice feedback settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SettingSwitchRow(
                label = "Voice feedback",
                checked = settings.enabled,
                onCheckedChange = preferences::setEnabled,
                testTag = TestTags.VOICE_FEEDBACK_ENABLED_SWITCH,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Disabled (not hidden) while the master switch above is off — their stored
            // values still persist either way, this only reflects that they don't
            // currently do anything. See specs/voice-feedback.md#settings.
            SettingSwitchRow(
                label = "Kilometers completed",
                checked = settings.announceKm,
                enabled = settings.enabled,
                onCheckedChange = preferences::setAnnounceKm,
                testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_SWITCH,
            )
            SettingSwitchRow(
                label = "Average speed per kilometer",
                checked = settings.announceSpeed,
                enabled = settings.enabled,
                onCheckedChange = preferences::setAnnounceSpeed,
                testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_SPEED_SWITCH,
            )
            SettingSwitchRow(
                label = "Steps per kilometer",
                checked = settings.announceSteps,
                enabled = settings.enabled,
                onCheckedChange = preferences::setAnnounceSteps,
                testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH,
            )
            SettingSwitchRow(
                label = "Elapsed time",
                checked = settings.announceElapsedTime,
                enabled = settings.enabled,
                onCheckedChange = preferences::setAnnounceElapsedTime,
                testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_ELAPSED_TIME_SWITCH,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}
