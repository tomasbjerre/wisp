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

    suspend fun getPoints(sessionId: Long): List<TrackPoint> = trackPointDao.getForSession(sessionId)

    suspend fun startSession(startedAt: Long): Long = sessionDao.insert(Session(startedAt = startedAt))

    suspend fun appendPoint(
        sessionId: Long,
        sequence: Int,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMps: Float?,
        segmentStart: Boolean,
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
            ),
        )
    }

    /** Recomputes and persists aggregate stats, then marks the session finished. */
    suspend fun finishSession(
        sessionId: Long,
        endedAt: Long,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        val summary = GeoUtils.summarize(trackPointDao.getForSession(sessionId))
        sessionDao.update(
            session.copy(
                endedAt = endedAt,
                distanceMeters = summary.distanceMeters,
                durationSeconds = summary.durationSeconds,
                averageSpeedMps = summary.averageSpeedMps,
                maxSpeedMps = summary.maxSpeedMps,
            ),
        )
    }

    suspend fun deleteSession(session: Session) = sessionDao.delete(session)

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

    /** See specs/tracking.md#what-must-survive-interruption. */
    suspend fun recoverUnfinishedSession(): Session? {
        val unfinished = sessionDao.findUnfinished() ?: return null
        val points = trackPointDao.getForSession(unfinished.id)
        val endedAt = points.lastOrNull()?.timestamp ?: unfinished.startedAt
        finishSession(unfinished.id, endedAt)
        return sessionDao.getById(unfinished.id)
    }
}
