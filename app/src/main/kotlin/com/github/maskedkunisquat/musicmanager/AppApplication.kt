package com.github.maskedkunisquat.musicmanager

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.maskedkunisquat.musicmanager.data.db.DatabaseFactory
import com.github.maskedkunisquat.musicmanager.data.repository.SimRepositoryImpl
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.worker.TickWorker
import java.util.concurrent.TimeUnit

class AppApplication : Application() {

    val aiProvider: LabelAiProvider by lazy { StubAiProvider() }

    val simRepository: SimRepository by lazy {
        SimRepositoryImpl(
            dao = DatabaseFactory.eventLogDao(this),
            engine = SimEngine(),
            aiProvider = aiProvider,
            seed = DEFAULT_SEED
        )
    }

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicTick()
    }

    private fun schedulePeriodicTick() {
        // Fire every hour; TickWorker uses elapsed-time logic to advance 0–6 game ticks per fire.
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
    }
}
