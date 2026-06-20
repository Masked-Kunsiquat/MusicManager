package com.github.maskedkunisquat.musicmanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity

@Database(entities = [EventLogEntity::class], version = 6, exportSchema = false)
abstract class SimDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}
