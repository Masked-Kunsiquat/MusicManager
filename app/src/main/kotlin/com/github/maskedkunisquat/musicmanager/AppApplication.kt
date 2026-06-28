package com.github.maskedkunisquat.musicmanager

import android.app.Application
import android.os.SystemClock
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppApplication : Application() {

    private val catchupMutex = Mutex()

    val aiProvider: GemmaLiteRtProvider by lazy { GemmaLiteRtProvider(this) }

    val modelDownloader: AndroidModelDownloader by lazy { AndroidModelDownloader(this) }

    val dao by lazy { DatabaseFactory.eventLogDao(this) }

    val simRepository: SimRepository by lazy {
        val prefs = getSharedPreferences(WORLD_PREFS, MODE_PRIVATE)
        SimRepositoryImpl(
            dao = dao,
            engine = SimEngine(),
            aiProvider = aiProvider,
            seed = DEFAULT_SEED,
            saveWorld = { world ->
                prefs.edit().putString(KEY_WORLD_SNAPSHOT, world.toJsonString()).commit()
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

    // Shared tick execution logic — called by TickWorker (background) and onResume (foreground).
    // The mutex ensures only one caller processes ticks at a time so ticksElapsed is never
    // double-counted if both paths run concurrently (e.g. WorkManager fires during an active session).
    suspend fun runCatchupIfDue() = catchupMutex.withLock {
        val prefs = getSharedPreferences(TICK_PREFS, MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val lastTickedAt = prefs.getLong(KEY_LAST_TICKED_AT, -1L)

        if (lastTickedAt == -1L || now < lastTickedAt) {
            prefs.edit().putLong(KEY_LAST_TICKED_AT, now).apply()
            return@withLock
        }

        val ticksElapsed = ((now - lastTickedAt) / TickWorker.TICK_INTERVAL_MS)
            .toInt().coerceIn(0, MAX_CATCHUP_TICKS)

        if (ticksElapsed > 0) {
            repeat(ticksElapsed) { i ->
                simRepository.tick()
                val isCapTick = ticksElapsed == MAX_CATCHUP_TICKS && i + 1 == ticksElapsed
                val checkpoint = if (isCapTick) now else lastTickedAt + (i + 1) * TickWorker.TICK_INTERVAL_MS
                prefs.edit().putLong(KEY_LAST_TICKED_AT, checkpoint).commit()
            }
        }
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
        const val TICK_PREFS = "tick_prefs"
        const val KEY_LAST_TICKED_AT = "last_ticked_at"
        private const val MAX_CATCHUP_TICKS = 24
    }
}
