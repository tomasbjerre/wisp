package com.github.tomasbjerre.wisp.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.github.tomasbjerre.wisp.MainActivity
import com.github.tomasbjerre.wisp.R
import com.github.tomasbjerre.wisp.WispApplication
import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.ui.splits.KmSplitRows
import com.github.tomasbjerre.wisp.util.CaloriesCalculator
import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns a recording session end to end: acquiring location, persisting
 * accepted points incrementally, and keeping a foreground notification so
 * Android doesn't kill recording while backgrounded. Fix filtering and
 * segment bookkeeping live in [TrackRecorder], which is unit tested
 * directly. See specs/tracking.md and specs/permissions-and-privacy.md.
 */
class TrackingService : LifecycleService() {
    private val repository by lazy { (application as WispApplication).repository }
    private val voiceFeedbackPreferences by lazy { (application as WispApplication).voiceFeedbackPreferences }
    private val unitPreferences by lazy { (application as WispApplication).unitPreferences }
    private val heartRatePreferences by lazy { (application as WispApplication).heartRatePreferences }
    private val activeRecordingStore by lazy { (application as WispApplication).activeRecordingStore }
    private val weightPreferences by lazy { (application as WispApplication).weightPreferences }
    private val heartRateMonitor by lazy { HeartRateMonitor(this) }
    private var heartRateRecorder = HeartRateRecorder()
    private var heartRateSettingJob: Job? = null
    private val locationTracker by lazy { LocationTracker(this) }
    private val geocodingService by lazy { GeocodingService(this) }
    private val stepCounterTracker by lazy { StepCounterTracker(this) }
    private var recorder = TrackRecorder()
    private var movementGate = MovementGate()
    private var stepRecorder = StepRecorder()
    private var stationaryGate = StationaryGate()
    private var voiceFeedbackSpeaker: VoiceFeedbackSpeaker? = null

    // See specs/voice-feedback.md: announces at most once per completed km.
    private var announcedCompleteKmCount = 0

    private var sessionId: Long? = null

    // See specs/calories.md: what this session's calories are calculated from, fixed when it
    // started (or read back from the stored session when it is continued after a kill).
    private var activityType: ActivityType? = null
    private var weightKg: Double? = null
    private var sequence = 0

    // See specs/data-model.md#trackpoint (pauseCause): why the most recent pause happened,
    // stamped on the point(s) that start the segment after it, cleared once one is accepted.
    private var pendingPauseCause: String? = null

    // See specs/tracking.md#session-lifecycle: what to do with fixes arriving while paused.
    // Non-null exactly while paused.
    private var pauseWatcher: PauseWatcher? = null

    // Elapsed time is a wall-clock ticker independent of GPS fix arrival — see
    // specs/tracking.md#location-sampling: fixes can be seconds apart, which made the
    // on-screen time look frozen between them rather than ticking like a stopwatch.
    private var tickerJob: Job? = null
    private var recordingStartElapsedRealtime = 0L
    private var pausedAccumulatedMillis = 0L
    private var pauseStartedElapsedRealtime = 0L

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // A null intent means the OS restarted this service after killing the process (see
        // START_STICKY below): continue the recording that was cut short.
        if (intent == null) resumeAfterKill()
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_FORCE_START -> forceStart()
            ACTION_PAUSE -> pause(PAUSE_CAUSE_MANUAL)
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            ACTION_REFRESH_NOTIFICATION -> updateNotification()
            ACTION_SET_ACTIVITY_TYPE ->
                ActivityType.fromId(intent.getStringExtra(EXTRA_ACTIVITY_TYPE))?.let(::setActivityType)
            ACTION_SET_WEIGHT -> setWeightKg(intent.getStringExtra(EXTRA_WEIGHT_KG)?.toDoubleOrNull())
        }
        return START_STICKY
    }

    private fun start() {
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(_state.value))
        recorder = TrackRecorder()
        movementGate = MovementGate()
        stepRecorder = StepRecorder()
        heartRateRecorder = HeartRateRecorder()
        observeHeartRateSetting()
        stationaryGate = StationaryGate()
        voiceFeedbackSpeaker = VoiceFeedbackSpeaker(this)
        announcedCompleteKmCount = 0
        sequence = 0
        pendingPauseCause = null
        pauseWatcher = null
        pausedAccumulatedMillis = 0L
        // The weight is read once, now: a later change of it must not change this session's
        // calories. The activity type starts as that of the last activity and can change while
        // recording — see specs/calories.md#activity-type.
        activityType = null
        weightKg = weightPreferences.weightKg.value
        lifecycleScope.launch {
            // A choice made in the meantime (see setActivityType) wins over the default.
            val default = repository.latestActivityType() ?: ActivityType.WALKING
            if (activityType == null) activityType = default
            val id = repository.startSession(System.currentTimeMillis(), activityType, weightKg)
            sessionId = id
            activeRecordingStore.sessionId = id
            _state.value =
                TrackingUiState(
                    isRecording = true,
                    isLocating = true,
                    isWaitingForMovement = true,
                    sessionId = id,
                    // As it is now, not as it was at start: it may have been changed since.
                    activityType = activityType,
                    weightKg = weightKg,
                )
            locationTracker.start(::onLocation)
            // Ticker starts once movement is confirmed, not here — see onLocation and
            // specs/tracking.md#start-gating.
        }
    }

    /**
     * See specs/tracking.md#what-must-survive-interruption: the OS restarted this service
     * after killing the process mid-recording, so carry on with the same session. The gap
     * is marked on the first point recorded afterwards ([RecordingResume.PAUSE_CAUSE_INTERRUPTED]).
     * A session that never confirmed movement is discarded instead.
     */
    private fun resumeAfterKill() {
        ensureNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification(_state.value))
        } catch (e: IllegalStateException) {
            // The OS won't let a restarted service go foreground (e.g. Android 12+ background
            // start limits). WispApplication ends the session at its last point instead.
            Log.w(TAG, "Could not resume recording in the foreground", e)
            stopSelf()
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not resume recording, missing permission", e)
            stopSelf()
            return
        }
        val id = activeRecordingStore.sessionId
        lifecycleScope.launch {
            val session = id?.let { repository.getSession(it) }
            val points = if (id != null && session?.endedAt == null) repository.getPoints(id) else emptyList()
            val plan = RecordingResume.plan(points)
            if (id == null || plan == null) {
                activeRecordingStore.sessionId = null
                if (id != null && session != null && session.endedAt == null) repository.deleteSessionById(id)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            continueSession(id, session, plan, points.filterNot { it.isNoise })
        }
    }

    private fun continueSession(
        id: Long,
        session: Session?,
        plan: ResumePlan,
        acceptedPoints: List<TrackPoint>,
    ) {
        recorder = TrackRecorder()
        movementGate = MovementGate()
        stepRecorder = StepRecorder(plan.steps)
        heartRateRecorder = HeartRateRecorder(plan.maxHeartRateBpm)
        observeHeartRateSetting()
        stationaryGate = StationaryGate()
        voiceFeedbackSpeaker = VoiceFeedbackSpeaker(this)
        val splits = GeoUtils.kmSplits(acceptedPoints, unitPreferences.unit.value)
        announcedCompleteKmCount = splits.completeSeconds.size
        sessionId = id
        activityType = ActivityType.fromId(session?.activityType)
        weightKg = session?.weightKg
        sequence = plan.nextSequence
        pendingPauseCause = RecordingResume.PAUSE_CAUSE_INTERRUPTED
        pauseWatcher = null
        pausedAccumulatedMillis = 0L
        // Time already recorded, not counting the gap while nothing was recording.
        recordingStartElapsedRealtime = SystemClock.elapsedRealtime() - plan.elapsedSeconds * 1_000
        _state.value =
            TrackingUiState(
                isRecording = true,
                sessionId = id,
                distanceMeters = plan.distanceMeters,
                elapsedSeconds = plan.elapsedSeconds,
                route = acceptedPoints.map { LatLon(it.latitude, it.longitude) },
                steps = plan.steps,
                latestKmSplitSeconds = splits.completeSeconds.lastOrNull(),
                fastestKmSplitSeconds = KmSplitRows.fastestSeconds(splits.completeSeconds),
                maxHeartRateBpm = plan.maxHeartRateBpm,
                activityType = activityType,
                weightKg = weightKg,
                kilocalories = currentKilocalories(plan.distanceMeters, plan.elapsedSeconds),
            )
        locationTracker.start(::onLocation)
        stepCounterTracker.start(stepRecorder::onStepCounterChanged)
        startHeartRateMonitor()
        startTicker()
    }

    /**
     * See specs/heart-rate.md#setting: the switch lives on Tracking, so it can flip while a
     * session is running — connect or disconnect right away when it does, provided the
     * session is at a point where a monitor would be connected (see [startHeartRateMonitor]).
     */
    private fun observeHeartRateSetting() {
        heartRateSettingJob?.cancel()
        heartRateSettingJob =
            lifecycleScope.launch {
                heartRatePreferences.enabled.drop(1).collect { enabled ->
                    val s = _state.value
                    if (!s.isRecording || s.isPaused || s.isWaitingForMovement) return@collect
                    if (enabled) startHeartRateMonitor() else stopHeartRateMonitor()
                }
            }
    }

    private fun pause(cause: String) {
        pendingPauseCause = cause
        // Location updates deliberately keep running — see specs/tracking.md#session-lifecycle:
        // what happens during a pause is stored as noise, and an automatic pause needs them
        // to notice movement resuming.
        pauseWatcher = PauseWatcher(autoResume = cause == PAUSE_CAUSE_AUTO)
        recorder.pause()
        stepRecorder.pause()
        heartRateRecorder.pause()
        heartRateMonitor.stop()
        pauseStartedElapsedRealtime = SystemClock.elapsedRealtime()
        stopTicker()
        _state.update { it.copy(isPaused = true) }
        updateNotification()
    }

    private fun resume() {
        pausedAccumulatedMillis += SystemClock.elapsedRealtime() - pauseStartedElapsedRealtime
        _state.update { it.copy(isPaused = false) }
        pauseWatcher = null
        stepRecorder.resume()
        heartRateRecorder.resume()
        startHeartRateMonitor()
        // Fresh idle clock (see specs/tracking.md#auto-pause) — otherwise time spent
        // paused would count toward the next auto-pause's idle threshold.
        stationaryGate = StationaryGate()
        startTicker()
        updateNotification()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob =
            lifecycleScope.launch {
                while (isActive) {
                    val elapsedMillis =
                        SystemClock.elapsedRealtime() - recordingStartElapsedRealtime - pausedAccumulatedMillis
                    _state.update {
                        it.copy(
                            elapsedSeconds = elapsedMillis / 1_000,
                            kilocalories = currentKilocalories(it.distanceMeters, elapsedMillis / 1_000),
                            // Also refreshed here so a lost monitor clears the display — see
                            // specs/heart-rate.md#recording.
                            heartRateBpm = currentHeartRate(),
                        )
                    }
                    updateNotification()
                    delay(1_000)
                }
            }
    }

    /**
     * See specs/calories.md#activity-type: changed while recording — stored on this session as
     * soon as it exists (it may not yet: [start] inserts it asynchronously, and reads
     * [activityType] when it does), and reflected in the calories now. Nothing else remembers
     * it: the next session starts as this session's type because this session stores it.
     */
    private fun setActivityType(type: ActivityType) {
        activityType = type
        _state.update {
            it.copy(activityType = type, kilocalories = currentKilocalories(it.distanceMeters, it.elapsedSeconds))
        }
        sessionId?.let { id -> lifecycleScope.launch { repository.updateActivityType(id, type) } }
        updateNotification()
    }

    /**
     * See specs/calories.md#weight: same as [setActivityType] — stored on this session as soon
     * as it exists and reflected in the calories now. Also persisted as the app-wide default
     * (see [WeightPreferences]) so the next session starts with it, same as it does today.
     */
    private fun setWeightKg(newWeightKg: Double?) {
        weightKg = newWeightKg
        weightPreferences.setWeightKg(newWeightKg)
        _state.update {
            it.copy(weightKg = newWeightKg, kilocalories = currentKilocalories(it.distanceMeters, it.elapsedSeconds))
        }
        sessionId?.let { id -> lifecycleScope.launch { repository.updateWeight(id, newWeightKg) } }
        updateNotification()
    }

    /** See specs/calories.md: null without an activity type and weight for this session. */
    private fun currentKilocalories(
        distanceMeters: Double,
        elapsedSeconds: Long,
    ): Double? {
        val activity = activityType ?: return null
        val weight = weightKg ?: return null
        return CaloriesCalculator.kilocalories(activity, weight, distanceMeters, elapsedSeconds)
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun stop() {
        // Before anything else: a recording that is being stopped must never be continued.
        activeRecordingStore.sessionId = null
        locationTracker.stop()
        stepCounterTracker.stop()
        heartRateMonitor.stop()
        stopTicker()
        voiceFeedbackSpeaker?.shutdown()
        voiceFeedbackSpeaker = null
        val id = sessionId
        // Never saw movement (see specs/tracking.md#start-gating) => no points were ever
        // recorded, so there's nothing to show — discard rather than saving a session
        // whose Detail screen would just be a permanent blank map. finishSession below
        // makes the same call from what was actually recorded, which also catches Force
        // start (specs/tracking.md#force-start) skipping this flag without any point
        // ever being recorded before Stop is tapped again.
        val neverMoved = _state.value.isWaitingForMovement
        val steps = stepRecorder.steps
        val maxHeartRateBpm = heartRateRecorder.maxBpm
        lifecycleScope.launch {
            val discarded =
                when {
                    id == null -> false
                    neverMoved -> {
                        repository.deleteSessionById(id)
                        true
                    }
                    else ->
                        !repository.finishSession(
                            id,
                            System.currentTimeMillis(),
                            steps = steps,
                            maxHeartRateBpm = maxHeartRateBpm,
                        )
                }
            _state.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    isWaitingForMovement = false,
                    wasDiscarded = discarded,
                    // Cleared only when discarded: a deleted session must never be
                    // navigated to. Otherwise kept around so observers can navigate to
                    // the finished session — it's only cleared implicitly when the next
                    // start() replaces the whole state.
                    sessionId = if (discarded) null else it.sessionId,
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            if (id != null && !discarded) lookUpNearestCity(id)
        }
    }

    /**
     * Runs on an independent scope, not [lifecycleScope], because it must outlive
     * [stopSelf] tearing this service down, and it's fine for it to finish after
     * navigation already moved on to the Detail screen — that screen observes the
     * session reactively and picks up the city once this resolves.
     */
    private fun lookUpNearestCity(sessionId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val firstPoint = repository.getPoints(sessionId).firstOrNull() ?: return@launch
            val city = geocodingService.nearestCity(firstPoint.latitude, firstPoint.longitude) ?: return@launch
            repository.updateNearestCity(sessionId, city)
        }
    }

    private fun onLocation(location: Location) {
        val id = sessionId ?: return
        val fix =
            LocationFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                speedMps = if (location.hasSpeed()) location.speed else null,
                timestampMillis = location.time,
            )

        if (handleStartGating(fix, id)) return
        if (_state.value.isPaused && handlePausedFix(fix, id)) return

        // See specs/tracking.md#auto-pause: a sustained lack of movement while actively
        // recording pauses the session automatically, exactly like a manual Pause tap.
        // The fix that trips auto-pause is still a fix like any other: recorded below (as
        // noise or not, per TrackRecorder) before the pause takes effect, never dropped.
        val shouldAutoPause = stationaryGate.onFix(fix)

        val recorded = recorder.accept(fix)
        val pauseCause = if (recorded.segmentStart) pendingPauseCause else null
        if (!recorded.isNoise) pendingPauseCause = null

        lifecycleScope.launch {
            val seq = sequence++
            repository.appendPoint(
                sessionId = id,
                sequence = seq,
                timestamp = recorded.timestampMillis,
                latitude = recorded.latitude,
                longitude = recorded.longitude,
                accuracyMeters = recorded.accuracyMeters,
                speedMps = recorded.speedMps,
                segmentStart = recorded.segmentStart,
                // See specs/tracking.md#km-splits: the running count, so steps can later be
                // split per km.
                steps = stepRecorder.steps,
                heartRateBpm = currentHeartRate(),
                isNoise = recorded.isNoise,
                noiseReason = recorded.noiseReason,
                pauseCause = pauseCause,
            )
            // See specs/tracking.md#noise: noise points are stored but never count
            // toward what the app itself computes or shows.
            val points = repository.getPoints(id).filterNot { it.isNoise }
            val summary = GeoUtils.summarize(points)
            val splits = GeoUtils.kmSplits(points, unitPreferences.unit.value)
            _state.update {
                it.copy(
                    distanceMeters = summary.distanceMeters,
                    currentSpeedMps = recorder.currentSpeedMps,
                    route = points.map { p -> LatLon(p.latitude, p.longitude) },
                    steps = stepRecorder.steps,
                    latestKmSplitSeconds = splits.completeSeconds.lastOrNull(),
                    fastestKmSplitSeconds = KmSplitRows.fastestSeconds(splits.completeSeconds),
                )
            }
            announceNewlyCompletedKm(splits)
            updateNotification()
        }

        if (shouldAutoPause) pause(PAUSE_CAUSE_AUTO)
    }

    /** See specs/voice-feedback.md. [VoiceFeedbackAnnouncement] decides what (if
     * anything) to say; this just speaks it and remembers the km it just announced. */
    private fun announceNewlyCompletedKm(splits: GeoUtils.KmSplits) {
        val text =
            VoiceFeedbackAnnouncement.forNewlyCompletedKm(
                splits = splits,
                previousCompleteCount = announcedCompleteKmCount,
                elapsedSeconds = _state.value.elapsedSeconds,
                settings = voiceFeedbackPreferences.settings.value,
                unit = unitPreferences.unit.value,
            )
        announcedCompleteKmCount = splits.completeSeconds.size
        if (text != null) voiceFeedbackSpeaker?.speak(text)
    }

    /**
     * See specs/tracking.md#session-lifecycle and #auto-pause. Returns true if [fix] has been
     * fully handled — stored as a noise point, because the session is still paused — or false
     * if it just ended an automatic pause, in which case the caller carries on and records it
     * as the first point of the resumed segment, exactly like any other fix.
     */
    private fun handlePausedFix(
        fix: LocationFix,
        id: Long,
    ): Boolean {
        val watcher = pauseWatcher ?: PauseWatcher(autoResume = false).also { pauseWatcher = it }
        val outcome = watcher.onFix(fix)
        if (outcome.shouldResume) {
            resume()
            return false
        }
        recordNoisePoint(fix, id, outcome.noiseReason)
        return true
    }

    /**
     * See specs/tracking.md#start-gating. Returns true once this [fix] has been fully
     * handled by start-gating and the caller should stop processing it further — either
     * it was rejected by the accuracy filter, or movement still isn't confirmed yet. A
     * fix handled here is stored as a noise point (see [recordNoisePoint]) either way —
     * see specs/tracking.md#noise.
     */
    private fun handleStartGating(
        fix: LocationFix,
        id: Long,
    ): Boolean {
        if (!_state.value.isWaitingForMovement) return false
        if (fix.accuracyMeters > TrackRecorder.MAX_ACCEPTABLE_ACCURACY_METERS) {
            val reasons = listOf(NoiseReason.POOR_ACCURACY, NoiseReason.BEFORE_MOVEMENT)
            recordNoisePoint(fix, id, NoiseReason.join(reasons))
            return true
        }

        val startedMoving = movementGate.hasStartedMoving(fix)
        _state.update { it.copy(isLocating = false, route = listOf(LatLon(fix.latitude, fix.longitude))) }
        if (!startedMoving) {
            recordNoisePoint(fix, id, NoiseReason.join(listOf(NoiseReason.BEFORE_MOVEMENT)))
            updateNotification()
            return true
        }

        confirmMovementStarted()
        return false
    }

    /**
     * See specs/tracking.md#noise: a fix seen before movement is ever confirmed never
     * counts toward the session's own stats, but is still stored so exported data has
     * every measurement Wisp saw, not just the ones it trusted — a user can then decide
     * for themselves what to do with it.
     */
    private fun recordNoisePoint(
        fix: LocationFix,
        id: Long,
        noiseReason: String?,
    ) {
        lifecycleScope.launch {
            val seq = sequence++
            repository.appendPoint(
                sessionId = id,
                sequence = seq,
                timestamp = fix.timestampMillis,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                speedMps = fix.speedMps,
                segmentStart = seq == 0,
                // The running count, so a stretch walked while paused (or waiting) is visible.
                steps = stepRecorder.steps,
                isNoise = true,
                noiseReason = noiseReason,
            )
        }
    }

    /**
     * See specs/tracking.md#start-gating: lets the user skip waiting for movement to be
     * detected automatically, e.g. when GPS is slow to acquire a usable fix. A no-op once
     * movement is already confirmed (or if a session isn't waiting for it at all).
     */
    private fun forceStart() {
        if (!_state.value.isWaitingForMovement) return
        confirmMovementStarted()
    }

    private fun confirmMovementStarted() {
        recordingStartElapsedRealtime = SystemClock.elapsedRealtime()
        _state.update { it.copy(isWaitingForMovement = false) }
        startTicker()
        // See specs/tracking.md#step-count: gated the same as the timer/track, so steps
        // taken before movement is confirmed don't count.
        stepCounterTracker.start(stepRecorder::onStepCounterChanged)
        startHeartRateMonitor()
    }

    /** See specs/heart-rate.md#connecting: only when the user has turned the setting on. */
    private fun startHeartRateMonitor() {
        if (!heartRatePreferences.enabled.value) return
        heartRateMonitor.start { bpm ->
            heartRateRecorder.onReading(bpm, SystemClock.elapsedRealtime())
            _state.update { it.copy(heartRateBpm = currentHeartRate(), maxHeartRateBpm = heartRateRecorder.maxBpm) }
        }
    }

    private fun stopHeartRateMonitor() {
        heartRateMonitor.stop()
        _state.update { it.copy(heartRateBpm = null) }
    }

    private fun currentHeartRate(): Int? = heartRateRecorder.currentBpm(SystemClock.elapsedRealtime())

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(_state.value))
    }

    private fun buildNotification(state: TrackingUiState): Notification {
        // See specs/permissions-and-privacy.md#required-access: opens Tracking, not Home.
        val openTracking =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text = TrackingNotificationText.forState(state, unitPreferences.unit.value)
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openTracking)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "TrackingService"

        const val ACTION_START = "com.github.tomasbjerre.wisp.action.START"
        const val ACTION_FORCE_START = "com.github.tomasbjerre.wisp.action.FORCE_START"
        const val ACTION_PAUSE = "com.github.tomasbjerre.wisp.action.PAUSE"
        const val ACTION_RESUME = "com.github.tomasbjerre.wisp.action.RESUME"
        const val ACTION_STOP = "com.github.tomasbjerre.wisp.action.STOP"
        const val ACTION_REFRESH_NOTIFICATION = "com.github.tomasbjerre.wisp.action.REFRESH_NOTIFICATION"
        const val ACTION_SET_ACTIVITY_TYPE = "com.github.tomasbjerre.wisp.action.SET_ACTIVITY_TYPE"
        private const val EXTRA_ACTIVITY_TYPE = "com.github.tomasbjerre.wisp.extra.ACTIVITY_TYPE"

        const val ACTION_SET_WEIGHT = "com.github.tomasbjerre.wisp.action.SET_WEIGHT"

        // A string, not a double extra: Intent has no nullable-double extra, and a null here
        // means "clear the weight" — same reasoning as WeightPreferences' own storage.
        private const val EXTRA_WEIGHT_KG = "com.github.tomasbjerre.wisp.extra.WEIGHT_KG"

        // Stored on TrackPoint.pauseCause — see specs/data-model.md#trackpoint.
        private const val PAUSE_CAUSE_MANUAL = "manual"
        private const val PAUSE_CAUSE_AUTO = "auto"

        private val _state = MutableStateFlow(TrackingUiState())
        val state: StateFlow<TrackingUiState> = _state

        private fun intent(
            context: Context,
            action: String,
        ) = Intent(context, TrackingService::class.java).setAction(action)

        fun start(context: Context) = context.startForegroundService(intent(context, ACTION_START))

        fun forceStart(context: Context) = context.startService(intent(context, ACTION_FORCE_START))

        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))

        fun resume(context: Context) = context.startService(intent(context, ACTION_RESUME))

        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))

        /**
         * Posts the recording notification again. One posted while notifications were not allowed
         * was dropped, and nothing else posts it again until the next location fix — which a
         * user standing still may not produce for a while.
         */
        fun setActivityType(
            context: Context,
            type: ActivityType,
        ) = context.startService(intent(context, ACTION_SET_ACTIVITY_TYPE).putExtra(EXTRA_ACTIVITY_TYPE, type.id))

        /** See specs/calories.md#weight. Null clears it. */
        fun setWeightKg(
            context: Context,
            weightKg: Double?,
        ) = context.startService(intent(context, ACTION_SET_WEIGHT).putExtra(EXTRA_WEIGHT_KG, weightKg?.toString()))

        fun refreshNotification(context: Context) = context.startService(intent(context, ACTION_REFRESH_NOTIFICATION))
    }
}
