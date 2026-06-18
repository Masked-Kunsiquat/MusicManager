package com.github.maskedkunisquat.musicmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.github.maskedkunisquat.musicmanager.worker.TickWorker

/**
 * Debug-only: force a tick catchup immediately.
 *   adb shell am broadcast -a com.github.maskedkunisquat.DEBUG_TICK \
 *       -p com.github.maskedkunisquat.musicmanager
 * Resets last_ticked_at to 0 so TickWorker computes maximum catchup (9 ticks).
 */
class DebugTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences("tick_prefs", Context.MODE_PRIVATE)
            .edit().putLong("last_ticked_at", 0L).commit()
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<TickWorker>().build())
    }
}
