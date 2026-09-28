package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the user's body weight in kilograms, or none — see specs/calories.md#weight. Plain
 * SharedPreferences, same reasoning as [UnitPreferences]: one value, no queries.
 */
class WeightPreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _weightKg = MutableStateFlow(read())
    val weightKg: StateFlow<Double?> = _weightKg

    /** Null clears it. */
    fun setWeightKg(weightKg: Double?) {
        prefs
            .edit()
            .apply {
                if (weightKg == null) remove(KEY_WEIGHT_KG) else putString(KEY_WEIGHT_KG, weightKg.toString())
            }.apply()
        _weightKg.value = weightKg
    }

    // A string, not a float: a float would turn 70.3 into 70.30000305175781.
    private fun read(): Double? = prefs.getString(KEY_WEIGHT_KG, null)?.toDoubleOrNull()

    private companion object {
        const val PREFS_NAME = "weight"
        const val KEY_WEIGHT_KG = "weight_kg"
    }
}
