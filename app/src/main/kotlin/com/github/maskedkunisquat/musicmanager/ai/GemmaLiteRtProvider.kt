package com.github.maskedkunisquat.musicmanager.ai

import android.content.Context
import android.os.Build
import android.util.Log
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelDownloader
import com.github.maskedkunisquat.musicmanager.logic.ai.ModelLoadState
import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class GemmaLiteRtProvider(private val context: Context) : LabelAiProvider {

    private val _modelLoadState = MutableStateFlow(ModelLoadState.IDLE)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    @Volatile private var engine: Engine? = null
    @Volatile private var openClFailed = false
    private val engineLock = ReentrantReadWriteLock()
    private val initLock = Any()
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stub = StubAiProvider()

    private val modelFile: File get() = GemmaModelConfig.modelFile(context)

    // Called from AppApplication.onCreate() and DownloadCompleteReceiver — non-blocking.
    fun initialize() {
        val state = _modelLoadState.value
        if (state == ModelLoadState.LOADING || state == ModelLoadState.READY) return
        initScope.launch { doInitialize() }
    }

    private fun doInitialize() {
        if (!modelFile.exists()) {
            _modelLoadState.value = ModelLoadState.IDLE
            return
        }
        synchronized(initLock) {
            if (_modelLoadState.value == ModelLoadState.READY) return
            _modelLoadState.value = ModelLoadState.LOADING
            Log.i(TAG, "Loading engine — file=${modelFile.name} board=${Build.BOARD}")

            var lastException: Exception? = null
            for (backend in selectBackends()) {
                var eng: Engine? = null
                try {
                    eng = Engine(EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        cacheDir = context.cacheDir.absolutePath,
                    ))
                    eng.initialize()
                    engineLock.writeLock().withLock { engine = eng }
                    _modelLoadState.value = ModelLoadState.READY
                    Log.i(TAG, "Engine ready — backend=${backend::class.simpleName}")
                    return
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Init failed with ${backend::class.simpleName}: ${e.message}")
                    (eng as? AutoCloseable)?.runCatching { close() }
                }
            }

            _modelLoadState.value = ModelLoadState.ERROR
            Log.e(TAG, "All backends failed", lastException)
        }
    }

    fun downloadModel(downloader: ModelDownloader): Long {
        if (modelFile.exists()) {
            initialize()
            return -1L
        }
        _modelLoadState.value = ModelLoadState.DOWNLOADING
        return runCatching {
            downloader.enqueue(
                modelFile = GemmaModelConfig.MODEL_FILENAME,
                url = "${GemmaModelConfig.HF_BASE_URL}/${GemmaModelConfig.MODEL_FILENAME}",
                sha256 = null  // Phase 2: fill in from HF model card
            )
        }.getOrElse { e ->
            Log.e(TAG, "Failed to enqueue model download", e)
            _modelLoadState.value = ModelLoadState.ERROR
            -1L
        }
    }

    override suspend fun generateEmail(event: SimEvent, world: SimWorld): GeneratedEmail {
        val eng = engineLock.readLock().run { lock(); try { engine } finally { unlock() } }
        if (eng == null) return stub.generateEmail(event, world)

        val artist = world.artists[event.artistId]
        return withContext(Dispatchers.IO) {
            runCatching { infer(eng, event, artist, world) }
                .getOrElse { e ->
                    if (!openClFailed && e.message?.contains("OpenCL", ignoreCase = true) == true) {
                        Log.w(TAG, "OpenCL failure — switching to CPU fallback")
                        openClFailed = true
                        engineLock.writeLock().withLock {
                            (engine as? AutoCloseable)?.runCatching { close() }
                            engine = null
                        }
                        _modelLoadState.value = ModelLoadState.IDLE
                        doInitialize()
                        // Re-check engine after CPU reinit; fall back to stub if it failed.
                        val cpuEng = engineLock.readLock().run { lock(); try { engine } finally { unlock() } }
                        if (cpuEng != null) runCatching { infer(cpuEng, event, artist, world) }
                            .getOrElse { stub.generateEmail(event, world) }
                        else stub.generateEmail(event, world)
                    } else {
                        Log.w(TAG, "Inference error — stub fallback: ${e.message}")
                        stub.generateEmail(event, world)
                    }
                }
        }
    }

    private suspend fun infer(
        eng: Engine,
        event: SimEvent,
        artist: ArtistState?,
        world: SimWorld
    ): GeneratedEmail {
        val tokens = StringBuilder()
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction()))
        eng.createConversation(config).use { conv ->
            conv.sendMessageAsync(buildPrompt(event, artist)).collect { msg ->
                val text = msg.toString().stripControlChars()
                if (text.isNotEmpty()) tokens.append(text)
            }
        }
        return parseResponse(tokens.toString(), event, world)
    }

    private suspend fun parseResponse(raw: String, event: SimEvent, world: SimWorld): GeneratedEmail {
        val fallback = stub.generateEmail(event, world)
        return try {
            val json = raw.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(json)
            val subject = obj.optString("subject").ifEmpty { fallback.subject }
            val body = obj.optString("body").ifEmpty { fallback.body }
            GeneratedEmail(subject = subject, body = body, options = fallback.options)
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed — stub fallback: ${e.message}")
            fallback
        }
    }

    private fun systemInstruction() =
        "You are a music artist writing a real email to your label manager. " +
        "Respond ONLY with a JSON object, no markdown, no code blocks: " +
        "{\"subject\": \"subject here\", \"body\": \"body here\"}\n" +
        "Subject: 5-10 words, lowercase, no punctuation at end. " +
        "Body: 3-5 sentences, first-person. End with your name on its own line: — [name]."

    private fun buildPrompt(event: SimEvent, artist: ArtistState?): String = buildString {
        val name = artist?.name ?: "the artist"
        val genre = artist?.genre ?: "indie"
        append("You are $name, a $genre music artist.\n\n")

        when (event) {
            is SimEvent.NeedUrgent -> {
                val topic = when (event.needType) {
                    NeedType.CREATIVE_FULFILLMENT -> "creative fulfillment"
                    NeedType.FINANCIAL_SECURITY -> "financial security"
                    NeedType.RECOGNITION -> "feeling recognized and visible"
                    NeedType.BELONGING -> "feeling connected and belonging"
                    NeedType.AUTONOMY -> "creative autonomy"
                }
                append("Write an email about your need for $topic — it's been neglected")
                if (event.currentValue < 0.20f) append(". This has become urgent")
                append(".\n\n")
            }
            is SimEvent.ContractExpiring -> {
                append("Write an email about your contract renewal. About ${event.daysRemaining} in-game days remain.\n\n")
            }
            is SimEvent.WantSurfaced -> {
                val want = event.wantType.name.lowercase(Locale.ROOT).replace('_', ' ')
                append("Write an email to your label manager about your desire to $want.\n\n")
            }
        }

        val dims = artist?.dimensions
        if (dims != null) {
            append("Communication style: ")
            when {
                dims.confidence < 0.35f -> append("you tend to hedge and second-guess yourself. ")
                dims.confidence > 0.65f -> append("you are direct and assertive. ")
                else -> append("you are measured and professional. ")
            }
            when {
                dims.loyalty < 0.35f -> append("Your relationship with the label feels strained. ")
                dims.loyalty > 0.65f -> append("You trust and value this partnership. ")
            }
            when {
                dims.volatility > 0.65f -> append("You express emotions openly.")
                dims.volatility < 0.35f -> append("You are composed and restrained.")
            }
        }
    }

    private fun selectBackends(): List<Backend> {
        val board = Build.BOARD.lowercase(Locale.ROOT)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val hasNpu = File(nativeLibDir, "libpenguin.so").exists()
        return when {
            (board == "sun" || board == "kailua" || board.startsWith("sm8750")) && hasNpu ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir), Backend.GPU(), Backend.CPU())
            (board == "kalama" || board.startsWith("sm8650")) && hasNpu ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir), Backend.GPU(), Backend.CPU())
            isQualcommDevice() ->
                if (!openClFailed) listOf(Backend.GPU(), Backend.CPU()) else listOf(Backend.CPU())
            else -> listOf(Backend.CPU())
        }
    }

    private fun isQualcommDevice(): Boolean {
        val hw = Build.HARDWARE.lowercase(Locale.ROOT)
        val board = Build.BOARD.lowercase(Locale.ROOT)
        return hw == "qcom" || board.contains("sm8") || board.contains("sdm") ||
            board == "sun" || board == "kailua" || board == "pineapple" || board == "kalama"
    }

    companion object {
        private const val TAG = "GemmaLiteRtProvider"
        private val CONTROL_CHARS = Regex("[\\p{Cntrl}&&[^\n\r\t]]")
        private fun String.stripControlChars() = replace(CONTROL_CHARS, "")
    }
}
