package com.github.tomasbjerre.wisp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds Session.steps (see specs/tracking.md#step-count). Real users already have
 * schema version 2 — every release shipped so far (v0.1.0-v0.3.0, per CHANGELOG.md)
 * used it — so this must preserve their existing sessions, not wipe them (see
 * specs/data-model.md#data-integrity-on-start). Existing rows get steps = 0, same as
 * any session recorded before this feature existed.
 */
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * Adds TrackPoint.steps (see specs/tracking.md#km-splits). Existing points get 0 — the
 * same as a device with no step sensor — so older sessions simply have no steps per km,
 * while keeping every session and point they already had.
 */
internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE track_points ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * Adds Session.maxHeartRateBpm and TrackPoint.heartRateBpm (see specs/heart-rate.md).
 * Both nullable with no default, so existing sessions and points simply have no heart
 * rate — the same as a session recorded with the setting off.
 */
internal val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN maxHeartRateBpm INTEGER")
            db.execSQL("ALTER TABLE track_points ADD COLUMN heartRateBpm INTEGER")
        }
    }

@Database(entities = [Session::class, TrackPoint::class], version = 5, exportSchema = false)
abstract class WispDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun trackPointDao(): TrackPointDao

    companion object {
        fun build(context: Context): WispDatabase =
            Room
                .databaseBuilder(context.applicationContext, WispDatabase::class.java, "wisp.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // Safety net only, not the primary path — see
                // specs/data-model.md#data-integrity-on-start. Every schema change that has
                // actually shipped to real users has an explicit Migration above; this only
                // catches a version Wisp never shipped (e.g. a version older than any real
                // release), where there's no real data to preserve anyway.
                .fallbackToDestructiveMigration()
                .build()
    }
}
