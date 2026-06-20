package com.github.maskedkunisquat.musicmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRaw(event: EventLogEntity)

    // Computes SHA-256 hashes and links to the previous row before persisting.
    open suspend fun insert(event: EventLogEntity) {
        val hash = sha256(event.payload)
        val prev = lastPayloadHash() ?: ""
        insertRaw(event.copy(payloadHash = hash, prevHash = prev))
    }

    // Reads all rows in insertion order and verifies the hash chain is unbroken.
    // Returns false (and logs nothing here — caller handles warnings) if any link is broken.
    open suspend fun verifyChain(): Boolean = verifyChainOf(getAllOrdered())

    @Query("SELECT payloadHash FROM event_log ORDER BY recordedAt DESC LIMIT 1")
    protected abstract suspend fun lastPayloadHash(): String?

    @Query("SELECT * FROM event_log ORDER BY recordedAt ASC")
    protected abstract suspend fun getAllOrdered(): List<EventLogEntity>

    @Query("SELECT * FROM event_log ORDER BY dayOfGame ASC, recordedAt ASC")
    abstract suspend fun getAll(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE dayOfGame >= :fromDay ORDER BY dayOfGame ASC, recordedAt ASC")
    abstract suspend fun getFromDay(fromDay: Int): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE selectedOptionId IS NULL ORDER BY dayOfGame ASC, recordedAt ASC")
    abstract fun observeUnresolved(): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE selectedOptionId IS NULL")
    abstract suspend fun getUnresolved(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE eventType = 'response_applied'")
    abstract suspend fun getResponseEntities(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE eventType = :eventType ORDER BY dayOfGame DESC, recordedAt DESC")
    abstract fun observeByType(eventType: String): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE eventType IN (:types) ORDER BY dayOfGame DESC, recordedAt DESC")
    abstract fun observeByTypes(types: List<String>): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE eventType = 'lead_surfaced' AND selectedOptionId IS NULL ORDER BY dayOfGame ASC, recordedAt ASC")
    abstract fun observeActiveSurfacedLeads(): Flow<List<EventLogEntity>>

    // WHERE selectedOptionId IS NULL guards against double-resolve silently overwriting the first choice.
    @Query("UPDATE event_log SET selectedOptionId = :optionId, resolvedAt = :resolvedAt WHERE id = :id AND selectedOptionId IS NULL")
    abstract suspend fun markResolved(id: String, optionId: String, resolvedAt: Long)

    @Query("UPDATE event_log SET viewedAt = :viewedAt WHERE id = :id AND viewedAt IS NULL")
    abstract suspend fun markViewed(id: String, viewedAt: Long)

    @Transaction
    open suspend fun resolveWithFollowUps(
        eventId: String,
        optionId: String,
        now: Long,
        responseEntity: EventLogEntity,
        injectedEntities: List<EventLogEntity>
    ) {
        markResolved(eventId, optionId, now)
        insert(responseEntity)
        injectedEntities.forEach { insert(it) }
    }
}
