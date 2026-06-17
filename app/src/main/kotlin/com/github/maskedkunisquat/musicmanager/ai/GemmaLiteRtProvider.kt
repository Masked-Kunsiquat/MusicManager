package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelDownloader
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelLoadState
import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GemmaLiteRtProvider(
    @Suppress("unused") private val context: Context
) : LabelAiProvider {

    private val _modelLoadState = MutableStateFlow(ModelLoadState.IDLE)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val stub = StubAiProvider()

    fun initialize() {
        // Phase 2: check if model file exists in context.filesDir; if yes, load LiteRT-LM Engine
        // and advance _modelLoadState → LOADING → READY (or ERROR).
    }

    fun downloadModel(downloader: ModelDownloader): Long {
        _modelLoadState.value = ModelLoadState.DOWNLOADING
        return downloader.enqueue(
            modelFile = GemmaModelConfig.MODEL_FILENAME,
            url = "${GemmaModelConfig.HF_BASE_URL}/${GemmaModelConfig.MODEL_FILENAME}",
            sha256 = null  // Phase 2: fill in SHA-256 from HF model card
        )
    }

    override fun generateEmail(event: SimEvent, world: SimWorld): GeneratedEmail =
        stub.generateEmail(event, world)
}
