package com.github.maskedkunisquat.musicmanager.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelDownloader

class AndroidModelDownloader(private val context: Context) : ModelDownloader {

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun enqueue(modelFile: String, url: String, sha256: String?): Long {
        // DownloadManager cannot write to internal storage; reject early if external is unavailable.
        if (context.getExternalFilesDir(null) == null) return -1L
        val dest = GemmaModelConfig.modelFile(context)
        if (dest.exists()) return -1L
        dest.parentFile?.mkdirs()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading AI model")
            .setDescription("Gemma 4 E4B — one-time download (~3.66 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(dest))
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)

        return dm.enqueue(request)
    }
}
