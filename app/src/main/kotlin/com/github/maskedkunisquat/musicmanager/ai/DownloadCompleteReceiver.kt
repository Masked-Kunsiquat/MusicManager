package com.github.maskedkunisquat.musicmanager.ai

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.maskedkunisquat.musicmanager.AppApplication

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        // initialize() checks whether the model file now exists before doing any work,
        // so firing on unrelated downloads is a cheap no-op.
        (context.applicationContext as AppApplication).aiProvider.initialize()
    }
}
