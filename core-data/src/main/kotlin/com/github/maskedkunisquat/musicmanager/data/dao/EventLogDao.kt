package com.github.maskedkunisquat.musicmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity

@Dao
interface EventLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventLogEntity)

    @Query("SELECT * FROM event_log ORDER BY dayOfGame ASC, recordedAt ASC")
    suspend fun getAll(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE dayOfGame >= :fromDay ORDER BY dayOfGame ASC, recordedAt ASC")
    suspend fun getFromDay(fromDay: Int): List<EventLogEntity>
}
