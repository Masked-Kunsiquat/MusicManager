package com.github.maskedkunisquat.musicmanager.data.db

import android.content.Context
import androidx.room.Room
import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao

object DatabaseFactory {
    fun eventLogDao(context: Context): EventLogDao =
        Room.databaseBuilder(context.applicationContext, SimDatabase::class.java, "sim.db")
            .fallbackToDestructiveMigration(true)
            .build()
            .eventLogDao()
}
