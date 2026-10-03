package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists whether the paused-session reminder pulses — see
 * specs/tracking.md#paused-session-reminder and specs/data-model.md#paused-reminder-setting.
 * Plain SharedPreferences, same reasoning as [HeartRatePreferences]: one flag, no queries.
 *
 * The one setting in Wisp that defaults to *on* — see specs/overview.md#design-principle.
 */
class PausedReminderPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "paused_reminder"
        const val KEY_ENABLED = "enabled"
    }
}
