package com.github.tomasbjerre.wisp.data

import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlinx.coroutines.flow.Flow

/**
 * Storage-facing operations required by specs/data-model.md#required-queries.
 * Wraps the DAOs so the rest of the app never talks to Room directly.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val trackPointDao: TrackPointDao,
) {
    fun observeSessions(): Flow<List<Session>> = sessionDao.observeAll()

    fun observeSession(id: Long): Flow<Session?> = sessionDao.observeById(id)

    suspend fun getSession(id: Long): Session? = sessionDao.getById(id)

    suspend fun getPoints(sessionId: Long): List<TrackPoint> = trackPointDao.getForSession(sessionId)

    /** [activityType] and [weightKg] are stored as they are now — see specs/calories.md. */
    suspend fun startSession(
        startedAt: Long,
        activityType: ActivityType? = null,
        weightKg: Double? = null,
    ): Long = sessionDao.insert(Session(startedAt = startedAt, activityType = activityType?.id, weightKg = weightKg))

    // One column per recorded field, mirroring TrackPoint itself.
    @Suppress("LongParameterList")
    suspend fun appendPoint(
        sessionId: Long,
        sequence: Int,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMps: Float?,
        segmentStart: Boolean,
        steps: Long = 0,
        heartRateBpm: Int? = null,
        isNoise: Boolean = false,
        noiseReason: String? = null,
        pauseCause: String? = null,
    ) {
        trackPointDao.insert(
            TrackPoint(
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                speedMps = speedMps,
                segmentStart = segmentStart,
                steps = steps,
                heartRateBpm = heartRateBpm,
                isNoise = isNoise,
                noiseReason = noiseReason,
                pauseCause = pauseCause,
            ),
        )
    }

    /**
     * Recomputes and persists aggregate stats, then marks the session finished. Returns
     * `false` and deletes the session instead when it has no real data to show — every
     * point it has is noise (see specs/tracking.md#noise), same as
     * [recoverUnfinishedSessions] already discards below. This is the case Force start
     * (specs/tracking.md#force-start) can produce: it skips the "waiting for movement"
     * flag that a live Stop otherwise checks (see `TrackingService.stop`), so a session
     * stopped again before any point was actually recorded would otherwise still be
     * saved as an empty, broken-looking history row.
     *
     * [steps] defaults to 0 — recovery (below) has no live sensor to read it from, see
     * specs/tracking.md#step-count. [maxHeartRateBpm] defaults to the highest heart rate
     * among the session's own points for the same reason — see specs/heart-rate.md#recording.
     */
    suspend fun finishSession(
        sessionId: Long,
        endedAt: Long,
        steps: Long = 0,
        maxHeartRateBpm: Int? = null,
    ): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        // See specs/tracking.md#noise: noise points are stored but never count toward
        // the session's own stats.
        val points = trackPointDao.getForSession(sessionId).filterNot { it.isNoise }
        if (points.isEmpty()) {
            sessionDao.delete(session)
            return false
        }
        val summary = GeoUtils.summarize(points)
        sessionDao.update(
            session.copy(
                endedAt = endedAt,
                distanceMeters = summary.distanceMeters,
                durationSeconds = summary.durationSeconds,
                averageSpeedMps = summary.averageSpeedMps,
                maxSpeedMps = summary.maxSpeedMps,
                steps = steps,
                maxHeartRateBpm = maxHeartRateBpm ?: points.mapNotNull { it.heartRateBpm }.maxOrNull(),
            ),
        )
        return true
    }

    /**
     * See specs/calories.md#activity-type: what a new session starts as — the type of the most
     * recent session that has one, so a deleted or discarded session no longer counts. Null when
     * there is none.
     */
    suspend fun latestActivityType(): ActivityType? = ActivityType.fromId(sessionDao.latestActivityType())

    /** See specs/calories.md#activity-type: changed while the session is being recorded. */
    suspend fun updateActivityType(
        sessionId: Long,
        activityType: ActivityType,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(activityType = activityType.id))
    }

    suspend fun deleteSession(session: Session) = sessionDao.delete(session)

    suspend fun deleteSessionById(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.delete(session)
    }

    /**
     * Best-effort enrichment, set after the session is already finished — see
     * com.github.tomasbjerre.wisp.location.GeocodingService.
     */
    suspend fun updateNearestCity(
        sessionId: Long,
        city: String,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(nearestCity = city))
    }

    /**
     * See specs/tracking.md#what-must-survive-interruption and
     * specs/data-model.md#data-integrity-on-start. Recovers every session left marked
     * as still recording, not just the most recent one — ordinarily there's at most
     * one, but this must never assume that. A session killed before it ever recorded a
     * point (still "locating" or "waiting for movement" — see
     * specs/tracking.md#start-gating) has nothing to recover, and finishing it anyway
     * would leave a broken zero-point entry in history — discard it instead, same as a
     * live Stop during that window (see TrackingService.stop). [skipSessionId] is left
     * alone: a session the OS is about to continue recording — see
     * specs/tracking.md#what-must-survive-interruption.
     */
    suspend fun recoverUnfinishedSessions(skipSessionId: Long? = null): List<Session> {
        val recovered = mutableListOf<Session>()
        for (unfinished in sessionDao.findAllUnfinished().filterNot { it.id == skipSessionId }) {
            // See specs/tracking.md#noise: a session with only noise points never
            // actually recorded anything real, same as one with no points at all.
            val points = trackPointDao.getForSession(unfinished.id).filterNot { it.isNoise }
            if (points.isEmpty()) {
                sessionDao.delete(unfinished)
                continue
            }
            val endedAt = points.lastOrNull()?.timestamp ?: unfinished.startedAt
            finishSession(unfinished.id, endedAt)
            sessionDao.getById(unfinished.id)?.let { recovered += it }
        }
        return recovered
    }
}
