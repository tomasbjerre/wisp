package com.github.tomasbjerre.wisp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies specs/data-model.md#data-integrity-on-start: real users already have schema
 * version 2 (every release shipped so far — see WispDatabase's own doc comment), so
 * MIGRATION_2_3 must preserve an existing session rather than silently wiping it. Seeds
 * a real, file-based schema-version-2 database (no mocks, no exported-schema fixtures —
 * [LegacyDatabaseV2] below mirrors what WispDatabase actually was at that version), then
 * reopens the same file through the real, current [WispDatabase.build] — the exact
 * production path, migration included.
 */
@RunWith(RobolectricTestRunner::class)
class WispDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `MIGRATION_2_3 preserves an existing session and adds steps defaulting to zero`() =
        runTest {
            val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV2::class.java, DB_NAME).build()
            val sessionId =
                legacyDb.sessionDao().insert(
                    LegacySessionV2(
                        startedAt = 1_000,
                        endedAt = 2_000,
                        distanceMeters = 500.0,
                        durationSeconds = 300,
                        averageSpeedMps = 1.5,
                        maxSpeedMps = 3.0,
                        nearestCity = "Stockholm",
                    ),
                )
            legacyDb.close()

            val database = WispDatabase.build(context)
            val session = database.sessionDao().getById(sessionId)
            database.close()

            assertThat(session).isNotNull
            assertThat(session!!.startedAt).isEqualTo(1_000L)
            assertThat(session.endedAt).isEqualTo(2_000L)
            assertThat(session.distanceMeters).isEqualTo(500.0)
            assertThat(session.durationSeconds).isEqualTo(300L)
            assertThat(session.averageSpeedMps).isEqualTo(1.5)
            assertThat(session.maxSpeedMps).isEqualTo(3.0)
            assertThat(session.nearestCity).isEqualTo("Stockholm")
            assertThat(session.steps).isZero()
        }

    private companion object {
        const val DB_NAME = "wisp.db"
    }
}

@Entity(tableName = "sessions")
internal data class LegacySessionV2(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val averageSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val nearestCity: String? = null,
)

@Dao
internal interface LegacySessionV2Dao {
    @Insert
    suspend fun insert(session: LegacySessionV2): Long
}

// Mirrors WispDatabase exactly as it was at schema version 2 — TrackPoint is unchanged
// since then, included so the real WispDatabase reopening this same file below finds
// every table it expects.
@Database(entities = [LegacySessionV2::class, TrackPoint::class], version = 2, exportSchema = false)
internal abstract class LegacyDatabaseV2 : RoomDatabase() {
    abstract fun sessionDao(): LegacySessionV2Dao
}
