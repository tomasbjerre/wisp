package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the chosen [UnitSystem] — see specs/units.md#persistence. Plain
 * SharedPreferences, not Room, same reasoning as [VoiceFeedbackPreferences]: one flag,
 * no queries/relations/migrations needed.
 */
class UnitPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _unit = MutableStateFlow(readUnit())
    val unit: StateFlow<UnitSystem> = _unit

    fun setUnit(unit: UnitSystem) {
        prefs.edit().putString(KEY_UNIT, unit.name).apply()
        _unit.value = unit
    }

    private fun readUnit(): UnitSystem {
        val stored = prefs.getString(KEY_UNIT, null) ?: return UnitSystem.METRIC
        return UnitSystem.valueOf(stored)
    }

    private companion object {
        const val PREFS_NAME = "units"
        const val KEY_UNIT = "unit_system"
    }
}
