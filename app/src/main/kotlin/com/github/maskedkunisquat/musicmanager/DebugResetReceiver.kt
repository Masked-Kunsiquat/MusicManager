package com.github.maskedkunisquat.musicmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as AppApplication).debugReset()
        // Kill the process so SimRepositoryImpl's cached DAO (now backed by a closed DB)
        // is never used again. The app relaunches clean on next open.
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
