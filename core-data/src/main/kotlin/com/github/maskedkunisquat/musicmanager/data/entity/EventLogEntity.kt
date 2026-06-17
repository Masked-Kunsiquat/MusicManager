package com.github.maskedkunisquat.musicmanager.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_log",
    indices = [Index(value = ["dayOfGame", "recordedAt"])]
)
data class EventLogEntity(
    @PrimaryKey val id: String,
    val dayOfGame: Int,
    val eventType: String,
    val payload: String,        // JSON blob
    val recordedAt: Long        // System.currentTimeMillis()
)
