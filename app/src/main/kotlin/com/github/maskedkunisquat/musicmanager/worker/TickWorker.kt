package com.github.maskedkunisquat.musicmanager.worker

import android.content.Context
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
        val now = System.currentTimeMillis()
        val lastTickedAt = prefs.getLong(KEY_LAST_TICKED_AT, -1L)

        if (lastTickedAt == -1L) {
            // First worker fire — record baseline without ticking; ViewModel.initializeIfEmpty handles seed ticks.
            prefs.edit().putLong(KEY_LAST_TICKED_AT, now).apply()
            return Result.success()
        }

        val ticksElapsed = ((now - lastTickedAt) / TICK_INTERVAL_MS)
            .toInt()
            .coerceIn(0, MAX_CATCHUP_TICKS)

        if (ticksElapsed > 0) {
            repeat(ticksElapsed) { app.simRepository.tick() }
            // Preserve fractional time within the current 4h window so it carries into the next fire.
            // When the cap fires (long absence), reset to now to avoid indefinite catchup.
            val newLastTickedAt = if (ticksElapsed < MAX_CATCHUP_TICKS) {
                lastTickedAt + ticksElapsed * TICK_INTERVAL_MS
            } else {
                now
            }
            prefs.edit().putLong(KEY_LAST_TICKED_AT, newLastTickedAt).apply()
        }

        return Result.success()
    }

    companion object {
        private const val PREFS_NAME = "tick_prefs"
        private const val KEY_LAST_TICKED_AT = "last_ticked_at"
        const val TICK_INTERVAL_MS = 4 * 60 * 60 * 1000L  // 4 hours per in-game day
        private const val MAX_CATCHUP_TICKS = 6            // cap at 24 hours of catchup
    }
}
