package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** See specs/voice-feedback.md#settings and specs/data-model.md#voice-feedback-settings. */
data class VoiceFeedbackSettings(
    val enabled: Boolean = false,
    val announceKm: Boolean = true,
    val announceSpeed: Boolean = true,
    val announceSteps: Boolean = true,
    val announceElapsedTime: Boolean = true,
)

/**
 * Persists [VoiceFeedbackSettings] — the one piece of user-facing configuration in
 * Wisp (see specs/overview.md#design-principle). Plain SharedPreferences, not Room:
 * this is a single set of flags, not a growing history, so it needs no queries,
 * relations, or migrations — same mechanism WispApplication already uses for
 * osmdroid's own config.
 */
class VoiceFeedbackPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<VoiceFeedbackSettings> = _settings

    fun setEnabled(enabled: Boolean) {
        update(KEY_ENABLED, enabled) { it.copy(enabled = enabled) }
    }

    fun setAnnounceKm(announce: Boolean) {
        update(KEY_ANNOUNCE_KM, announce) { it.copy(announceKm = announce) }
    }

    fun setAnnounceSpeed(announce: Boolean) {
        update(KEY_ANNOUNCE_SPEED, announce) { it.copy(announceSpeed = announce) }
    }

    fun setAnnounceSteps(announce: Boolean) {
        update(KEY_ANNOUNCE_STEPS, announce) { it.copy(announceSteps = announce) }
    }

    fun setAnnounceElapsedTime(announce: Boolean) {
        update(KEY_ANNOUNCE_ELAPSED_TIME, announce) { it.copy(announceElapsedTime = announce) }
    }

    private fun update(
        key: String,
        value: Boolean,
        apply: (VoiceFeedbackSettings) -> VoiceFeedbackSettings,
    ) {
        prefs.edit().putBoolean(key, value).apply()
        _settings.value = apply(_settings.value)
    }

    private fun readSettings() =
        VoiceFeedbackSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            announceKm = prefs.getBoolean(KEY_ANNOUNCE_KM, true),
            announceSpeed = prefs.getBoolean(KEY_ANNOUNCE_SPEED, true),
            announceSteps = prefs.getBoolean(KEY_ANNOUNCE_STEPS, true),
            announceElapsedTime = prefs.getBoolean(KEY_ANNOUNCE_ELAPSED_TIME, true),
        )

    private companion object {
        const val PREFS_NAME = "voice_feedback"
        const val KEY_ENABLED = "enabled"
        const val KEY_ANNOUNCE_KM = "announce_km"
        const val KEY_ANNOUNCE_SPEED = "announce_speed"
        const val KEY_ANNOUNCE_STEPS = "announce_steps"
        const val KEY_ANNOUNCE_ELAPSED_TIME = "announce_elapsed_time"
    }
}
