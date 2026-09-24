package com.github.tomasbjerre.wisp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, TrackPoint::class], version = 3, exportSchema = false)
abstract class WispDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun trackPointDao(): TrackPointDao

    companion object {
        fun build(context: Context): WispDatabase =
            Room
                .databaseBuilder(context.applicationContext, WispDatabase::class.java, "wisp.db")
                // Wisp has no public release with real user data yet (see ../../CHANGELOG.md),
                // so a real Migration isn't worth writing for this schema bump. Add one before
                // this matters, i.e. before a schema change ships to real users.
                .fallbackToDestructiveMigration()
                .build()
    }
}
