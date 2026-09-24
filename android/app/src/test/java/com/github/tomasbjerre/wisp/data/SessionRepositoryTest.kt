package com.github.tomasbjerre.wisp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the storage layer against a real in-memory SQLite database
 * (via Room + Robolectric) rather than mocked DAOs, so these tests verify
 * the actual queries required by specs/data-model.md#required-queries.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private lateinit var database: WispDatabase
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WispDatabase::class.java)
                .build()
        repository = SessionRepository(database.sessionDao(), database.trackPointDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `points are persisted incrementally during a session, not only at stop`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 1_000)

            repository.appendPoint(sessionId, 0, 1_000, 59.0, 18.0, 5f, null, segmentStart = true)
            assertThat(repository.getPoints(sessionId)).hasSize(1)

            repository.appendPoint(sessionId, 1, 2_000, 59.001, 18.0, 5f, null, segmentStart = false)
            assertThat(repository.getPoints(sessionId)).hasSize(2)
        }

    @Test
    fun `sessions are listed most recent first`() =
        runTest {
            val older = repository.startSession(startedAt = 1_000)
            repository.finishSession(older, endedAt = 1_500)
            val newer = repository.startSession(startedAt = 2_000)
            repository.finishSession(newer, endedAt = 2_500)

            val sessions = repository.observeSessions().first()

            assertThat(sessions.map { it.id }).containsExactly(newer, older)
        }

    @Test
    fun `a session still recording does not appear in the list`() =
        runTest {
            // See specs/data-model.md#required-queries: only finished sessions belong in
            // history — an in-progress or not-yet-recovered session must stay invisible.
            val finished = repository.startSession(startedAt = 1_000)
            repository.finishSession(finished, endedAt = 1_500)
            repository.startSession(startedAt = 2_000)

            val sessions = repository.observeSessions().first()

            assertThat(sessions.map { it.id }).containsExactly(finished)
        }

    @Test
    fun `a session's points load in sequence order`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 1_000)
            // Insert out of sequence order to prove the query orders them, not the insert order.
            repository.appendPoint(sessionId, 2, 3_000, 59.002, 18.0, 5f, null, segmentStart = false)
            repository.appendPoint(sessionId, 0, 1_000, 59.000, 18.0, 5f, null, segmentStart = true)
            repository.appendPoint(sessionId, 1, 2_000, 59.001, 18.0, 5f, null, segmentStart = false)

            val points = repository.getPoints(sessionId)

            assertThat(points.map { it.sequence }).containsExactly(0, 1, 2)
        }

    @Test
    fun `deleting a session removes its points too`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 1_000)
            repository.appendPoint(sessionId, 0, 1_000, 59.0, 18.0, 5f, null, segmentStart = true)
            val session = repository.observeSession(sessionId).first()!!

            repository.deleteSession(session)

            assertThat(repository.getPoints(sessionId)).isEmpty()
            assertThat(repository.observeSession(sessionId).first()).isNull()
        }

    @Test
    fun `finishing a session persists distance, duration, average and max speed`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 0)
            repository.appendPoint(sessionId, 0, 0, 59.0000, 18.0, 5f, 1f, segmentStart = true)
            repository.appendPoint(sessionId, 1, 10_000, 59.0010, 18.0, 5f, 9f, segmentStart = false)

            repository.finishSession(sessionId, endedAt = 10_000)

            val session = repository.observeSession(sessionId).first()!!
            assertThat(session.endedAt).isEqualTo(10_000L)
            assertThat(session.distanceMeters).isPositive()
            assertThat(session.durationSeconds).isEqualTo(10L)
            assertThat(session.maxSpeedMps).isCloseTo(9.0, within(0.0001))
        }

    @Test
    fun `the nearest city can be recorded for a finished session`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 0)
            repository.finishSession(sessionId, endedAt = 1_000)

            repository.updateNearestCity(sessionId, "Stockholm")

            val session = repository.observeSession(sessionId).first()!!
            assertThat(session.nearestCity).isEqualTo("Stockholm")
        }

    @Test
    fun `an unfinished session is recovered and finalized at its last point`() =
        runTest {
            // Simulates the app being killed mid-recording (specs/tracking.md#what-must-survive-interruption).
            val sessionId = repository.startSession(startedAt = 0)
            repository.appendPoint(sessionId, 0, 0, 59.0, 18.0, 5f, null, segmentStart = true)
            repository.appendPoint(sessionId, 1, 5_000, 59.001, 18.0, 5f, null, segmentStart = false)

            val recovered = repository.recoverUnfinishedSession()

            assertThat(recovered?.id).isEqualTo(sessionId)
            assertThat(recovered?.endedAt).isEqualTo(5_000L)
        }

    @Test
    fun `there is nothing to recover when every session already ended`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 0)
            repository.finishSession(sessionId, endedAt = 1_000)

            assertThat(repository.recoverUnfinishedSession()).isNull()
        }

    @Test
    fun `an unfinished session with no points is discarded, not recovered`() =
        runTest {
            // Simulates the app being killed while still "locating" or "waiting for
            // movement" (specs/tracking.md#start-gating) — never recorded a point.
            val sessionId = repository.startSession(startedAt = 0)

            val recovered = repository.recoverUnfinishedSession()

            assertThat(recovered).isNull()
            assertThat(repository.observeSession(sessionId).first()).isNull()
        }

    @Test
    fun `a session can be discarded by id`() =
        runTest {
            val sessionId = repository.startSession(startedAt = 0)

            repository.deleteSessionById(sessionId)

            assertThat(repository.observeSession(sessionId).first()).isNull()
        }
}
