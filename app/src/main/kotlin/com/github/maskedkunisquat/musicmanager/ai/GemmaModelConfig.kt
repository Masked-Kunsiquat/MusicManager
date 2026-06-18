package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

object GemmaModelConfig {
    const val HF_BASE_URL =
        "https://huggingface.co/masked-kunsiquat/gemma-3-1b-it-litert/resolve/main"

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
