package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import java.io.File

object GemmaModelConfig {
    const val HF_BASE_URL =
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main"
    const val MODEL_FILENAME = "gemma-4-E4B-it.litertlm"

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, MODEL_FILENAME)
}
