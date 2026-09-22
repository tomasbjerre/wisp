package com.github.tomasbjerre.wisp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPoint): Long

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY sequence ASC")
    suspend fun getForSession(sessionId: Long): List<TrackPoint>

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int
}
