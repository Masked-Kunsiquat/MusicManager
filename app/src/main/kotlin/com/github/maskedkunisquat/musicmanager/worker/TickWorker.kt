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
        (applicationContext as AppApplication).runCatchupIfDue()
        return Result.success()
    }

    companion object {
        const val TICK_INTERVAL_MS = 60 * 60 * 1000L   // 1h per tick (season = 90 ticks ≈ 3.75 real days)
    }
}
