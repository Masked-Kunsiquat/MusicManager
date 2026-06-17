package com.github.maskedkunisquat.musicmanager.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_log",
    indices = [
        Index(value = ["dayOfGame", "recordedAt"]),
        Index(value = ["selectedOptionId", "dayOfGame", "recordedAt"])
    ]
)
data class EventLogEntity(
    @PrimaryKey val id: String,
    val dayOfGame: Int,
    val eventType: String,
    val payload: String,        // JSON blob of event-specific fields
    val recordedAt: Long,       // System.currentTimeMillis()
    val emailSubject: String,   // AI-generated prose (stub in Phase 1)
    val emailBody: String,
    val selectedOptionId: String?,  // null = pending player response
    val resolvedAt: Long?           // null = pending
)
