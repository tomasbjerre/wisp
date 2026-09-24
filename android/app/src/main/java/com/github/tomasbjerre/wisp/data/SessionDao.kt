package com.github.tomasbjerre.wisp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    /** See specs/data-model.md#required-queries — only finished sessions belong in history. */
    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<Session?>

    /**
     * Recovery path from specs/tracking.md — every session never finalized on last run.
     * Not limited to one: see specs/data-model.md#data-integrity-on-start.
     */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC")
    suspend fun findAllUnfinished(): List<Session>
}
