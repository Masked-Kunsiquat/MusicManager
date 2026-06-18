package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

object GemmaModelConfig {
    const val RELEASE_BASE_URL =
        "https://github.com/Masked-Kunsiquat/MusicManager/releases/download/models-v1"

    fun modelFilename(): String {
        val board = Build.BOARD.lowercase(Locale.ROOT)
        return when {
            board == "sun" || board == "kailua" || board.startsWith("sm8750") ->
                "gemma3-1b-it-elite.litertlm"
            board == "kalama" || board.startsWith("sm8650") ->
                "gemma3-1b-it-ultra.litertlm"
            else ->
                "gemma3-1b-it-universal.litertlm"
        }
    }

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, modelFilename())
}
