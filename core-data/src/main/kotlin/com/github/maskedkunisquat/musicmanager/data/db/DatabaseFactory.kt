package com.github.maskedkunisquat.musicmanager.data.db

import android.content.Context
import androidx.room.Room
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao

object DatabaseFactory {
    @Volatile private var instance: EventLogDao? = null

    fun eventLogDao(context: Context): EventLogDao = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            SimDatabase::class.java,
            "sim.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
            .eventLogDao()
            .also { instance = it }
    }

    fun clearForDebug(context: Context) {
        context.applicationContext.deleteDatabase("sim.db")
        synchronized(this) { instance = null }
    }
}
