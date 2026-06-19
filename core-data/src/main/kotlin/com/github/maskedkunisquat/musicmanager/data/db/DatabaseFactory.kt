package com.github.maskedkunisquat.musicmanager.data.db

import android.content.Context
import androidx.room.Room
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao

object DatabaseFactory {
    @Volatile private var db: SimDatabase? = null

    fun eventLogDao(context: Context): EventLogDao = db?.eventLogDao() ?: synchronized(this) {
        db?.eventLogDao() ?: Room.databaseBuilder(
            context.applicationContext,
            SimDatabase::class.java,
            "sim.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
            .also { db = it }
            .eventLogDao()
    }

    fun clearForDebug(context: Context) {
        synchronized(this) {
            db?.close()
            db = null
            context.applicationContext.deleteDatabase("sim.db")
        }
    }
}
