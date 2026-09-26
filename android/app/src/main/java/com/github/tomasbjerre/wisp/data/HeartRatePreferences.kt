package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists whether heart rate recording is on — see specs/heart-rate.md#setting. Plain
 * SharedPreferences, same reasoning as [UnitPreferences]: one flag, no queries.
 */
class HeartRatePreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "heart_rate"
        const val KEY_ENABLED = "enabled"
    }
}
