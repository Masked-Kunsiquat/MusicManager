package com.github.maskedkunisquat.musicmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as AppApplication).debugReset()
    }
}
