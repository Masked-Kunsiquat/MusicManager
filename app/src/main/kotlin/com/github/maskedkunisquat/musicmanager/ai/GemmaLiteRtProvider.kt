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
import com.google.ai.edge.litertlm.Content
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class GemmaLiteRtProvider(private val context: Context) : LabelAiProvider {

    private val _modelLoadState = MutableStateFlow(ModelLoadState.IDLE)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    @Volatile private var engine: Engine? = null
    private val engineLock = ReentrantReadWriteLock()
    private val initLock = Any()
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stub = StubAiProvider()

    private val modelFile: File get() = GemmaModelConfig.modelFile(context)

    fun initialize() {
        val state = _modelLoadState.value
        if (state == ModelLoadState.LOADING || state == ModelLoadState.READY) return
        // Set LOADING eagerly so observers waiting on modelLoadState see it immediately,
        // before the background coroutine has a chance to start and set it itself.
        _modelLoadState.value = ModelLoadState.LOADING
        initScope.launch { doInitialize() }
    }

    private fun doInitialize() {
        if (!modelFile.exists()) {
            _modelLoadState.value = ModelLoadState.IDLE
            return
        }
        val expectedHash = GemmaModelConfig.expectedSha256For(GemmaModelConfig.modelFilename(context))
        if (expectedHash != null) {
            val actual = computeSha256(modelFile)
            if (actual != expectedHash) {
                Log.e(TAG, "SHA-256 mismatch for ${modelFile.name} — deleting corrupted download")
                if (!modelFile.delete()) {
                    Log.w(TAG, "Failed to delete corrupted file — manual removal may be required: ${modelFile.absolutePath}")
                }
                _modelLoadState.value = ModelLoadState.ERROR
                return
            }
        }
        synchronized(initLock) {
            if (_modelLoadState.value == ModelLoadState.READY) return
            _modelLoadState.value = ModelLoadState.LOADING
            Log.i(TAG, "Loading engine — file=${modelFile.name} board=${Build.BOARD}")

            var lastException: Throwable? = null
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
                } catch (e: Throwable) {
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
            val filename = GemmaModelConfig.modelFilename(context)
            downloader.enqueue(
                modelFile = filename,
                url = "${GemmaModelConfig.RELEASE_BASE_URL}/$filename",
                sha256 = GemmaModelConfig.expectedSha256For(filename)
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
                    Log.w(TAG, "Inference error — stub fallback: ${e.message}")
                    stub.generateEmail(event, world)
                }
        }
    }

    private suspend fun infer(
        eng: Engine,
        event: SimEvent,
        artist: ArtistState?,
        world: SimWorld
    ): GeneratedEmail {
        val fallback = stub.generateEmail(event, world)
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction(fallback.options.size)))
        val raw = eng.createConversation(config).use { conv ->
            conv.sendMessage(buildPrompt(event, artist, fallback.options.size))
                .contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }
        Log.i(TAG, "Gemma raw: ${raw.take(200)}")
        return parseEmail(raw, fallback)
    }

    // Extracts the first {...} block from raw output and parses subject/body/options.
    // The 1B model often wraps JSON in markdown fences — first/last brace search handles that.
    private fun parseEmail(raw: String, fallback: GeneratedEmail): GeneratedEmail {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end <= start) {
            Log.w(TAG, "No JSON found — stub fallback")
            return fallback
        }
        return runCatching {
            val obj = JSONObject(raw.substring(start, end + 1))
            val subject = obj.optString("subject").trim()
            val body = obj.optString("body").trim()
                .replace(MARKDOWN_BOLD_ITALIC_RE, "$1")
            if (subject.isBlank() || body.isBlank()) return fallback

            // Merge Gemma option labels onto stub effects. Stub owns game logic (effects,
            // costs); Gemma owns the text the player reads. Fall back to stub labels if
            // count doesn't match or array is missing.
            val gemmaLabels = obj.optJSONArray("options")
            val options = if (gemmaLabels != null && gemmaLabels.length() == fallback.options.size) {
                fallback.options.mapIndexed { i, stub ->
                    val label = gemmaLabels.optString(i).trim()
                    if (label.isNotBlank()) stub.copy(text = label) else stub
                }
            } else {
                fallback.options
            }

            GeneratedEmail(subject = subject, body = body, options = options)
        }.getOrElse {
            Log.w(TAG, "JSON parse failed — stub fallback: ${it.message}")
            fallback
        }
    }

    private fun systemInstruction(optionCount: Int): String {
        val optionSlots = (1..optionCount).joinToString(", ") { "\"...\"" }
        return "You are a music artist writing emails to your label manager. " +
            "Respond with ONLY a JSON object — no other text, no markdown, no code fences:\n" +
            "{\"subject\": \"...\", \"body\": \"...\", \"options\": [$optionSlots]}\n" +
            "subject: 5-10 words, all lowercase, no terminal punctuation. " +
            "body: 3-5 sentences, first person, no asterisks. " +
            "options: exactly $optionCount short action phrases (5-10 words each) the label manager could offer in response."
    }

    private fun buildPrompt(event: SimEvent, artist: ArtistState?, optionCount: Int = 3): String = buildString {
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

        append("Provide exactly $optionCount options.\n\n")
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

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(65_536)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun selectBackends(): List<Backend> {
        val board = Build.BOARD.lowercase(Locale.ROOT)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val hasNpu = File(nativeLibDir, "libpenguin.so").exists()
        // GPU (WebGPU) backend native-crashes on Adreno 830 when loading this model due to
        // GPU memory exhaustion — the JVM can't catch a SIGSEGV, so GPU is excluded until
        // LiteRT WebGPU stability improves or we move init to an isolated process.
        return when {
            (board == "sun" || board == "kailua" || board.startsWith("sm8750")) && hasNpu ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir), Backend.CPU())
            (board == "kalama" || board.startsWith("sm8650")) && hasNpu ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir), Backend.CPU())
            else -> listOf(Backend.CPU())
        }
    }

    companion object {
        private const val TAG = "GemmaLiteRtProvider"
        private val MARKDOWN_BOLD_ITALIC_RE = Regex("\\*{1,2}([^*]+)\\*{1,2}")
    }
}
