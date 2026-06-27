package com.github.maskedkunisquat.musicmanager.worker

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.maskedkunisquat.musicmanager.AppApplication

class TickWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AppApplication
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // elapsedRealtime() is monotonic within a boot — immune to system clock changes.
        // It resets on reboot, so a negative delta means the device rebooted since our
        // last checkpoint; in that case we reset the baseline and skip ticking rather
        // than granting free ticks or crashing.
        val now = SystemClock.elapsedRealtime()
        val lastTickedAt = prefs.getLong(KEY_LAST_TICKED_AT, -1L)

        if (lastTickedAt == -1L || now < lastTickedAt) {
            // First fire, or device rebooted — establish fresh baseline.
            prefs.edit().putLong(KEY_LAST_TICKED_AT, now).apply()
            return Result.success()
        }

        val ticksElapsed = ((now - lastTickedAt) / TICK_INTERVAL_MS)
            .toInt()
            .coerceIn(0, MAX_CATCHUP_TICKS)

        if (ticksElapsed > 0) {
            repeat(ticksElapsed) { i ->
                app.simRepository.tick()
                // Commit synchronously after each tick so progress survives process death mid-loop.
                // On the final cap tick, reset to now to prevent indefinite catchup after long absences.
                val isCapTick = ticksElapsed == MAX_CATCHUP_TICKS && i + 1 == ticksElapsed
                val checkpoint = if (isCapTick) now else lastTickedAt + (i + 1) * TICK_INTERVAL_MS
                prefs.edit().putLong(KEY_LAST_TICKED_AT, checkpoint).commit()
            }
        }

        return Result.success()
    }

    companion object {
        private const val PREFS_NAME = "tick_prefs"
        private const val KEY_LAST_TICKED_AT = "last_ticked_at"
        const val TICK_INTERVAL_MS = 60 * 60 * 1000L   // 1h per tick (season = 90 ticks ≈ 3.75 real days)
        private const val MAX_CATCHUP_TICKS = 24        // cap at 24 hours of catchup (24 × 60min)
    }
}
