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
        (applicationContext as AppApplication).simRepository.tick()
        return Result.success()
    }
}
