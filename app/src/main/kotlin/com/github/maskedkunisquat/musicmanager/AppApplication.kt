package com.github.maskedkunisquat.musicmanager

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.maskedkunisquat.musicmanager.ai.AndroidModelDownloader
import com.github.maskedkunisquat.musicmanager.ai.GemmaLiteRtProvider
import com.github.maskedkunisquat.musicmanager.data.db.DatabaseFactory
import com.github.maskedkunisquat.musicmanager.data.db.toJsonString
import com.github.maskedkunisquat.musicmanager.data.db.toSimWorldOrNull
import com.github.maskedkunisquat.musicmanager.data.repository.SimRepositoryImpl
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.worker.TickWorker
import java.util.concurrent.TimeUnit

class AppApplication : Application() {

    val aiProvider: GemmaLiteRtProvider by lazy { GemmaLiteRtProvider(this) }

    val modelDownloader: AndroidModelDownloader by lazy { AndroidModelDownloader(this) }

    val simRepository: SimRepository by lazy {
        val prefs = getSharedPreferences(WORLD_PREFS, MODE_PRIVATE)
        SimRepositoryImpl(
            dao = DatabaseFactory.eventLogDao(this),
            engine = SimEngine(),
            aiProvider = aiProvider,
            seed = DEFAULT_SEED,
            saveWorld = { world ->
                prefs.edit().putString(KEY_WORLD_SNAPSHOT, world.toJsonString()).apply()
            },
            loadWorld = {
                prefs.getString(KEY_WORLD_SNAPSHOT, null)?.toSimWorldOrNull()
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        aiProvider.initialize()
        schedulePeriodicTick()
    }

    fun debugReset() {
        getSharedPreferences("tick_prefs", MODE_PRIVATE).edit().clear().commit()
        getSharedPreferences(WORLD_PREFS, MODE_PRIVATE).edit().clear().commit()
        DatabaseFactory.clearForDebug(this)
    }

    private fun schedulePeriodicTick() {
        // Fire every hour; TickWorker uses elapsed-time logic to advance 0–9 game ticks per fire.
        val request = PeriodicWorkRequestBuilder<TickWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TICK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        // Phase 2: persist seed across sessions (DataStore), allow new-game seed selection.
        private const val DEFAULT_SEED = 42L
        private const val TICK_WORK_NAME = "sim_tick"
        private const val WORLD_PREFS = "world_prefs"
        private const val KEY_WORLD_SNAPSHOT = "world_snapshot"
    }
}
