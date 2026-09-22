package com.github.tomasbjerre.wisp.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.github.tomasbjerre.wisp.MainActivity
import com.github.tomasbjerre.wisp.R
import com.github.tomasbjerre.wisp.WispApplication
import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val locationTracker by lazy { LocationTracker(this) }
    private val geocodingService by lazy { GeocodingService(this) }
    private var recorder = TrackRecorder()

    private var sessionId: Long? = null
    private var sequence = 0

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
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    private fun start() {
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(_state.value))
        recorder = TrackRecorder()
        sequence = 0
        recordingStartElapsedRealtime = SystemClock.elapsedRealtime()
        pausedAccumulatedMillis = 0L
        lifecycleScope.launch {
            val id = repository.startSession(System.currentTimeMillis())
            sessionId = id
            _state.value = TrackingUiState(isRecording = true, sessionId = id)
            locationTracker.start(::onLocation)
            startTicker()
        }
    }

    private fun pause() {
        locationTracker.stop()
        recorder.pause()
        pauseStartedElapsedRealtime = SystemClock.elapsedRealtime()
        stopTicker()
        _state.update { it.copy(isPaused = true) }
        updateNotification()
    }

    private fun resume() {
        pausedAccumulatedMillis += SystemClock.elapsedRealtime() - pauseStartedElapsedRealtime
        _state.update { it.copy(isPaused = false) }
        locationTracker.start(::onLocation)
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
                    _state.update { it.copy(elapsedSeconds = elapsedMillis / 1_000) }
                    updateNotification()
                    delay(1_000)
                }
            }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun stop() {
        locationTracker.stop()
        stopTicker()
        val id = sessionId
        lifecycleScope.launch {
            if (id != null) repository.finishSession(id, System.currentTimeMillis())
            // Keep sessionId around so observers can navigate to the finished session;
            // it's only cleared implicitly when the next start() replaces the whole state.
            _state.update { it.copy(isRecording = false, isPaused = false) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        if (id != null) lookUpNearestCity(id)
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
        val recorded = recorder.accept(fix) ?: return

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
            )
            val points = repository.getPoints(id)
            val summary = GeoUtils.summarize(points)
            _state.update {
                it.copy(
                    distanceMeters = summary.distanceMeters,
                    currentSpeedMps = recorder.currentSpeedMps,
                    route = points.map { p -> LatLon(p.latitude, p.longitude) },
                )
            }
            updateNotification()
        }
    }

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
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val distanceKm = state.distanceMeters / 1000.0
        val minutes = state.elapsedSeconds / 60
        val seconds = state.elapsedSeconds % 60
        val pausedSuffix = if (state.isPaused) " · paused" else ""
        val text = "%.2f km · %d:%02d%s".format(distanceKm, minutes, seconds, pausedSuffix)
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.github.tomasbjerre.wisp.action.START"
        const val ACTION_PAUSE = "com.github.tomasbjerre.wisp.action.PAUSE"
        const val ACTION_RESUME = "com.github.tomasbjerre.wisp.action.RESUME"
        const val ACTION_STOP = "com.github.tomasbjerre.wisp.action.STOP"

        private val _state = MutableStateFlow(TrackingUiState())
        val state: StateFlow<TrackingUiState> = _state

        private fun intent(
            context: Context,
            action: String,
        ) = Intent(context, TrackingService::class.java).setAction(action)

        fun start(context: Context) = context.startForegroundService(intent(context, ACTION_START))

        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))

        fun resume(context: Context) = context.startService(intent(context, ACTION_RESUME))

        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
    }
}
