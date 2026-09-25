package com.github.tomasbjerre.wisp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
 * version 2 or 3 (see WispDatabase's own doc comments), so MIGRATION_2_3 and
 * MIGRATION_3_4 must preserve existing sessions and points rather than silently wiping
 * them. Seeds a real, file-based database at the old version (no mocks, no
 * exported-schema fixtures — [LegacyDatabaseV2]/[LegacyDatabaseV3] below mirror what
 * WispDatabase actually was at each version), then reopens the same file through the
 * real, current [WispDatabase.build] — the exact production path, migration included.
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

    @Test
    fun `MIGRATION_3_4 preserves existing points and adds steps defaulting to zero`() =
        runTest {
            val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV3::class.java, DB_NAME).build()
            val sessionId = legacyDb.dao().insertSession(Session(startedAt = 1_000, endedAt = 2_000, steps = 1_234))
            legacyDb.dao().insertPoint(
                LegacyTrackPointV3(
                    sessionId = sessionId,
                    sequence = 0,
                    timestamp = 1_000,
                    latitude = 59.3293,
                    longitude = 18.0686,
                    accuracyMeters = 5f,
                    speedMps = 2.5f,
                    segmentStart = true,
                ),
            )
            legacyDb.close()

            val database = WispDatabase.build(context)
            val session = database.sessionDao().getById(sessionId)
            val points = database.trackPointDao().getForSession(sessionId)
            database.close()

            assertThat(session!!.steps).isEqualTo(1_234L)
            assertThat(points).hasSize(1)
            val point = points.single()
            assertThat(point.timestamp).isEqualTo(1_000L)
            assertThat(point.latitude).isEqualTo(59.3293)
            assertThat(point.longitude).isEqualTo(18.0686)
            assertThat(point.speedMps).isEqualTo(2.5f)
            assertThat(point.segmentStart).isTrue()
            assertThat(point.steps).isZero()
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

// TrackPoint as it was up to schema version 3 (before steps), pointing at version 2's
// sessions table. Separate from LegacyTrackPointV3 below only because Room requires a
// foreign key's parent entity to be part of the same @Database.
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = LegacySessionV2::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
internal data class LegacyTrackPointV2(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequence: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val segmentStart: Boolean,
)

// Mirrors WispDatabase exactly as it was at schema version 2, so the real WispDatabase
// reopening this same file below finds every table it expects.
@Database(entities = [LegacySessionV2::class, LegacyTrackPointV2::class], version = 2, exportSchema = false)
internal abstract class LegacyDatabaseV2 : RoomDatabase() {
    abstract fun sessionDao(): LegacySessionV2Dao
}

// TrackPoint as it was at schema version 3 (before steps). Session is unchanged since
// version 3, so the real entity stands in for it.
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
internal data class LegacyTrackPointV3(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequence: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val segmentStart: Boolean,
)

@Dao
internal interface LegacyV3Dao {
    @Insert
    suspend fun insertSession(session: Session): Long

    @Insert
    suspend fun insertPoint(point: LegacyTrackPointV3): Long
}

// Mirrors WispDatabase exactly as it was at schema version 3.
@Database(entities = [Session::class, LegacyTrackPointV3::class], version = 3, exportSchema = false)
internal abstract class LegacyDatabaseV3 : RoomDatabase() {
    abstract fun dao(): LegacyV3Dao
}
