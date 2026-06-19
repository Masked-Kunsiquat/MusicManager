package com.github.maskedkunisquat.musicmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventLogEntity)

    @Query("SELECT * FROM event_log ORDER BY dayOfGame ASC, recordedAt ASC")
    suspend fun getAll(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE dayOfGame >= :fromDay ORDER BY dayOfGame ASC, recordedAt ASC")
    suspend fun getFromDay(fromDay: Int): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE selectedOptionId IS NULL ORDER BY dayOfGame ASC, recordedAt ASC")
    fun observeUnresolved(): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE selectedOptionId IS NULL")
    suspend fun getUnresolved(): List<EventLogEntity>

    // WHERE selectedOptionId IS NULL guards against double-resolve silently overwriting the first choice.
    @Query("UPDATE event_log SET selectedOptionId = :optionId, resolvedAt = :resolvedAt WHERE id = :id AND selectedOptionId IS NULL")
    suspend fun markResolved(id: String, optionId: String, resolvedAt: Long)

    @Query("UPDATE event_log SET viewedAt = :viewedAt WHERE id = :id AND viewedAt IS NULL")
    suspend fun markViewed(id: String, viewedAt: Long)
}
