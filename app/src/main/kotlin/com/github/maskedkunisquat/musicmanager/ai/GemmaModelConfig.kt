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
            else -> "gemma3-1b-it-universal.litertlm"  // unrecognized NPU board — safe CPU fallback
        }
    }

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, modelFilename(context))

    // Expected SHA-256 hex digests keyed by filename (from GitHub Releases asset page).
    // Add new entries here when uploading a model. Absent = verification skipped for that file.
    private val expectedSha256 = mapOf(
        "gemma3-1b-it-universal.litertlm"            to "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        "gemma3-1b-it-elite.litertlm"                to "1904ceff9591e7a140df3a672c800e8e7bee8337526484b00f69ccef4fa2d60a",
        "gemma3-1b-it-ultra.litertlm"                to "85d2ea5199802f913818d53897b3a304bcf983abb993393e6b1749fbdb005552",
        "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm"   to "1904ceff9591e7a140df3a672c800e8e7bee8337526484b00f69ccef4fa2d60a",
        "Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm"   to "85d2ea5199802f913818d53897b3a304bcf983abb993393e6b1749fbdb005552",
        "Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm"   to "fda5dca0e8c1c6f65ca5625c326ff79920c7eb82625a0c6515ae4f5711957b1f",
    )

    fun expectedSha256For(filename: String): String? = expectedSha256[filename]
}