package com.github.maskedkunisquat.musicmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.maskedkunisquat.musicmanager.data.db.DatabaseFactory

/**
 * Debug-only: wipe game DB and tick prefs WITHOUT touching the model file.
 * Use this instead of `pm clear` so the model doesn't need to be re-downloaded.
 *
 *   adb shell am broadcast -a com.github.maskedkunisquat.DEBUG_RESET \
 *       -p com.github.maskedkunisquat.musicmanager
 *
 * After broadcasting, force-stop and relaunch the app to get a clean session.
 */
class DebugResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences("tick_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        DatabaseFactory.clearForDebug(context)
    }
}
