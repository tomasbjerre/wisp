package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remembers the last chosen [ActivityType] — see specs/calories.md#activity-type. Walking until
 * one has been chosen. Plain SharedPreferences, same reasoning as [UnitPreferences].
 */
class ActivityTypePreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activityType =
        MutableStateFlow(ActivityType.fromId(prefs.getString(KEY_ACTIVITY_TYPE, null)) ?: ActivityType.WALKING)
    val activityType: StateFlow<ActivityType> = _activityType

    fun setActivityType(activityType: ActivityType) {
        prefs.edit().putString(KEY_ACTIVITY_TYPE, activityType.id).apply()
        _activityType.value = activityType
    }

    private companion object {
        const val PREFS_NAME = "activity_type"
        const val KEY_ACTIVITY_TYPE = "activity_type"
    }
}
