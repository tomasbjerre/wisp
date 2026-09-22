package com.github.tomasbjerre.wisp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, TrackPoint::class], version = 1, exportSchema = false)
abstract class WispDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun trackPointDao(): TrackPointDao

    companion object {
        fun build(context: Context): WispDatabase =
            Room
                .databaseBuilder(context.applicationContext, WispDatabase::class.java, "wisp.db")
                .build()
    }
}
