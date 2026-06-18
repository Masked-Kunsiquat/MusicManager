package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

object GemmaModelConfig {
    const val RELEASE_BASE_URL =
        "https://github.com/Masked-Kunsiquat/MusicManager/releases/download/models-v1"

    // NPU-compiled models require libpenguin.so (Qualcomm AI Engine Direct).
    // Until the NPU backend is properly packaged, all devices use the universal CPU build.
    // TODO: when libpenguin.so is bundled, gate on hasNpu and select the device-specific model.
    fun modelFilename(context: Context): String {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val hasNpu = File(nativeLibDir, "libpenguin.so").exists()
        if (!hasNpu) return "gemma3-1b-it-universal.litertlm"

        val board = Build.BOARD.lowercase(Locale.ROOT)
        return when {
            board == "sun" || board == "kailua" || board.startsWith("sm8750") ->
                "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm"
            board == "kalama" || board.startsWith("sm8650") ->
                "Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm"
            else ->
                "Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm"
        }
    }

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, modelFilename(context))
}