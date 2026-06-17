package com.github.maskedkunisquat.musicmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey val id: String,
    val dayOfGame: Int,
    val eventType: String,
    val payload: String,        // JSON blob
    val recordedAt: Long        // System.currentTimeMillis()
)
