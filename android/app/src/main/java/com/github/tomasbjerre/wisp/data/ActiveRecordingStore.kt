package com.github.tomasbjerre.wisp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers which session is being recorded right now, so that after the app process is
 * killed and restarted it can tell a recording the OS is about to continue (see
 * specs/tracking.md#what-must-survive-interruption) from one that was simply abandoned.
 * Plain SharedPreferences, same reasoning as [UnitPreferences]: one value, no queries.
 */
class ActiveRecordingStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The session being recorded, or null when nothing is. */
    var sessionId: Long?
        get() = prefs.getLong(KEY_SESSION_ID, NONE).takeIf { it != NONE }
        set(value) {
            // commit(), not apply(): the marker must be on disk before the process can be killed.
            prefs.edit().putLong(KEY_SESSION_ID, value ?: NONE).commit()
        }

    private companion object {
        const val PREFS_NAME = "active_recording"
        const val KEY_SESSION_ID = "session_id"
        const val NONE = -1L
    }
}
