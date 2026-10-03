package com.github.tomasbjerre.wisp.ui.tracking

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.HeartRatePreferences
import com.github.tomasbjerre.wisp.data.PausedReminderPreferences
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.data.VoiceFeedbackPreferences
import com.github.tomasbjerre.wisp.data.VoiceFeedbackSettings
import com.github.tomasbjerre.wisp.location.HeartRateMonitor
import com.github.tomasbjerre.wisp.location.TrackingService
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.TestTags

/**
 * See specs/ui-flows.md#2a-settings. Reached from Tracking's gear icon, over that screen: the
 * one place weight (specs/calories.md#weight), the heart rate monitor setting
 * (specs/heart-rate.md#setting) and voice feedback (specs/voice-feedback.md#settings) are all
 * configured, since none of them make sense from Home or Detail — they only ever affect a live
 * recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    voiceFeedbackPreferences: VoiceFeedbackPreferences,
    heartRatePreferences: HeartRatePreferences,
    pausedReminderPreferences: PausedReminderPreferences,
    unit: UnitSystem,
    onBack: () -> Unit,
) {
    val voiceFeedbackSettings by voiceFeedbackPreferences.settings.collectAsStateWithLifecycle()

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
        // Scrollable, per specs/ui-flows.md#2a-settings: a plain Column measures whatever no
        // longer fits the screen with zero height — adding the "Vibrate while paused" row made
        // this list outgrow small screens (CI's emulator is 320x640), leaving the bottom voice
        // feedback switches unreachable. Every control on this screen must stay reachable on
        // any screen, now and as rows are added.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .testTag(TestTags.SETTINGS_LIST)
                    .padding(horizontal = 16.dp),
        ) {
            WeightSection(unit)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            HeartRateSettingSection(heartRatePreferences)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PausedReminderSettingSection(pausedReminderPreferences)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingSwitchRow(
                label = "Voice feedback",
                checked = voiceFeedbackSettings.enabled,
                onCheckedChange = voiceFeedbackPreferences::setEnabled,
                testTag = TestTags.VOICE_FEEDBACK_ENABLED_SWITCH,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            AnnouncementSwitches(voiceFeedbackSettings, voiceFeedbackPreferences, unit)
        }
    }
}

/**
 * See specs/calories.md#weight: available in any state a session is in — this screen is only
 * reachable from a live Tracking session — and takes effect on it immediately. Also remembered
 * as the default the next session starts with.
 */
@Composable
private fun WeightSection(unit: UnitSystem) {
    val context = LocalContext.current
    val state by TrackingService.state.collectAsStateWithLifecycle()
    // What is typed, kept as typed: reformatting the stored kilograms on every keystroke would
    // fight the user (and turn 70 lb into 69.9). Reset when the unit changes, since the same
    // typed digits would otherwise be shown under the wrong unit.
    var text by remember(unit) {
        mutableStateOf(state.weightKg?.let { Formatting.weightForEditing(it, unit) } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val value = typed.replace(',', '.').toDoubleOrNull()
            when {
                typed.isBlank() -> TrackingService.setWeightKg(context, null)
                value != null && value > 0 -> TrackingService.setWeightKg(context, unit.displayToKilograms(value))
            }
        },
        label = { Text("Weight (${unit.weightAbbreviation})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(TestTags.WEIGHT_FIELD),
    )
}

/**
 * See specs/heart-rate.md#setting. Turning it on first asks for the Bluetooth access it needs
 * (Android 12+ only — earlier versions have it at install time); declined leaves it off.
 * Disabled while there's no Bluetooth to reach a monitor with. The live reading it drives stays
 * on [TrackingScreen] itself, next to the rest of that screen's live stats.
 */
@Composable
private fun HeartRateSettingSection(heartRatePreferences: HeartRatePreferences) {
    val context = LocalContext.current
    val enabled by heartRatePreferences.enabled.collectAsStateWithLifecycle()
    val available = rememberHeartRateAvailable()

    // With nothing to connect to, the setting is switched off (and persisted as off)
    // rather than left on for a monitor that can't be reached.
    LaunchedEffect(available, enabled) {
        if (!available && enabled) heartRatePreferences.setEnabled(false)
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) heartRatePreferences.setEnabled(true)
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (available) "Heart rate monitor" else "Heart rate monitor (Bluetooth is off)",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = enabled && available,
            enabled = available,
            modifier = Modifier.testTag(TestTags.HEART_RATE_SWITCH),
            onCheckedChange = { wanted ->
                val missing =
                    if (wanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HeartRateMonitor.REQUIRED_PERMISSIONS_S.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                    } else {
                        emptyList()
                    }
                if (missing.isEmpty()) {
                    heartRatePreferences.setEnabled(wanted)
                } else {
                    launcher.launch(missing.toTypedArray())
                }
            },
        )
    }
}

/**
 * See specs/tracking.md#paused-session-reminder and specs/ui-flows.md#2a-settings: the
 * once-a-minute pulse saying a paused session is still open. On by default — the one setting
 * in Wisp that is (see specs/overview.md#design-principle) — and the switch exists only to
 * let someone refuse it. Takes effect on the session being recorded right away, like every
 * other control on this screen.
 */
@Composable
private fun PausedReminderSettingSection(preferences: PausedReminderPreferences) {
    val enabled by preferences.enabled.collectAsStateWithLifecycle()

    SettingSwitchRow(
        label = "Vibrate while paused",
        checked = enabled,
        onCheckedChange = preferences::setEnabled,
        testTag = TestTags.PAUSED_REMINDER_SWITCH,
    )
}

/** Whether Bluetooth can currently reach a monitor, kept current as it's turned on/off. */
@Composable
private fun rememberHeartRateAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(HeartRateMonitor.isAvailable(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    available = HeartRateMonitor.isAvailable(context)
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        available = HeartRateMonitor.isAvailable(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return available
}

/** The per-announcement switches — see specs/voice-feedback.md#settings. */
@Composable
private fun AnnouncementSwitches(
    settings: VoiceFeedbackSettings,
    preferences: VoiceFeedbackPreferences,
    unit: UnitSystem,
) {
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
        label = "Average speed per ${unit.distanceWordSingular}",
        checked = settings.announceSpeed,
        enabled = settings.enabled,
        onCheckedChange = preferences::setAnnounceSpeed,
        testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_SPEED_SWITCH,
    )
    SettingSwitchRow(
        label = "Steps per ${unit.distanceWordSingular}",
        checked = settings.announceSteps,
        enabled = settings.enabled,
        onCheckedChange = preferences::setAnnounceSteps,
        testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_STEPS_SWITCH,
    )
    SettingSwitchRow(
        label = "Elapsed time per ${unit.distanceWordSingular}",
        checked = settings.announceKmElapsedTime,
        enabled = settings.enabled,
        onCheckedChange = preferences::setAnnounceKmElapsedTime,
        testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_KM_ELAPSED_TIME_SWITCH,
    )
    SettingSwitchRow(
        label = "Total elapsed time",
        checked = settings.announceElapsedTime,
        enabled = settings.enabled,
        onCheckedChange = preferences::setAnnounceElapsedTime,
        testTag = TestTags.VOICE_FEEDBACK_ANNOUNCE_ELAPSED_TIME_SWITCH,
    )
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
