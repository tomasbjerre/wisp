package com.github.tomasbjerre.wisp

import android.app.Application
import android.content.Context
import com.github.tomasbjerre.wisp.data.ActiveRecordingStore
import com.github.tomasbjerre.wisp.data.HeartRatePreferences
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.VoiceFeedbackPreferences
import com.github.tomasbjerre.wisp.data.WispDatabase
import com.github.tomasbjerre.wisp.location.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

/**
 * Manual, framework-free service locator. The app is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class WispApplication : Application() {
    lateinit var repository: SessionRepository
        private set
    lateinit var voiceFeedbackPreferences: VoiceFeedbackPreferences
        private set
    lateinit var unitPreferences: UnitPreferences
    lateinit var heartRatePreferences: HeartRatePreferences
        private set
    lateinit var activeRecordingStore: ActiveRecordingStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = WispDatabase.build(this)
        repository = SessionRepository(database.sessionDao(), database.trackPointDao())
        voiceFeedbackPreferences = VoiceFeedbackPreferences(this)
        unitPreferences = UnitPreferences(this)
        heartRatePreferences = HeartRatePreferences(this)
        activeRecordingStore = ActiveRecordingStore(this)

        val osmdroidPrefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, osmdroidPrefs)
        Configuration.getInstance().userAgentValue = packageName

        // See specs/tracking.md#what-must-survive-interruption and
        // specs/data-model.md#data-integrity-on-start.
        val continuing = activeRecordingStore.sessionId
        CoroutineScope(Dispatchers.IO).launch {
            // A session that was being recorded when the process was killed is left alone
            // for now: TrackingService is about to be restarted by the OS and continue it.
            repository.recoverUnfinishedSessions(skipSessionId = continuing)
            if (continuing == null) return@launch
            delay(RESUME_GRACE_MILLIS)
            // The OS never brought the recording back (e.g. after a reboot or a force stop),
            // so end it at its last point like any other interrupted session. Only if
            // nothing else has taken over the marker since — a new recording, or a stop.
            val state = TrackingService.state.value
            val continued = state.isRecording && state.sessionId == continuing
            if (!continued && activeRecordingStore.sessionId == continuing) {
                activeRecordingStore.sessionId = null
                repository.recoverUnfinishedSessions()
            }
        }
    }

    private companion object {
        // How long the OS gets to restart a recording that was killed along with the process.
        const val RESUME_GRACE_MILLIS = 30_000L
    }
}
