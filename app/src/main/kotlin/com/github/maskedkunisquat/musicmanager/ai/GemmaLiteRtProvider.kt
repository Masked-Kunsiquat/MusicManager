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
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
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
            val actual = try {
                computeSha256(modelFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read model file for SHA-256 verification", e)
                _modelLoadState.value = ModelLoadState.ERROR
                return
            }
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

    override suspend fun generateEmail(
        event: SimEvent,
        world: SimWorld,
        history: List<ArtistInteractionEntry>
    ): GeneratedEmail {
        val eng = engineLock.readLock().run { lock(); try { engine } finally { unlock() } }
        if (eng == null) return stub.generateEmail(event, world, history)

        val artist = world.artists[event.artistId]
        return withContext(Dispatchers.IO) {
            runCatching { infer(eng, event, artist, world, history) }
                .getOrElse { e ->
                    Log.w(TAG, "Inference error — stub fallback: ${e.message}")
                    stub.generateEmail(event, world, history)
                }
        }
    }

    private suspend fun infer(
        eng: Engine,
        event: SimEvent,
        artist: ArtistState?,
        world: SimWorld,
        history: List<ArtistInteractionEntry>
    ): GeneratedEmail {
        val fallback = stub.generateEmail(event, world, history)
        val isArtistEmail = artist != null || event is SimEvent.NegotiationRound
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction(fallback.options.size, isArtistEmail)))
        val raw = eng.createConversation(config).use { conv ->
            conv.sendMessage(buildPrompt(event, artist, world, history, fallback.options.size))
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

    private fun systemInstruction(optionCount: Int, isArtistEmail: Boolean): String {
        val persona = if (isArtistEmail)
            "You are a music artist writing an email to your label manager."
        else
            "You are a music industry professional writing to a label manager."
        val optionSlots = (1..optionCount).joinToString(", ") { "\"...\"" }
        return "$persona " +
            "Respond with ONLY a JSON object — no other text, no markdown, no code fences:\n" +
            "{\"subject\": \"...\", \"body\": \"...\", \"options\": [$optionSlots]}\n" +
            "subject: 5-10 words, all lowercase, no terminal punctuation. " +
            "body: 3-5 sentences, first person, no asterisks. " +
            "options: exactly $optionCount short action phrases (5-10 words each) the label manager could offer in response."
    }

    private fun buildPrompt(
        event: SimEvent,
        artist: ArtistState?,
        world: SimWorld,
        history: List<ArtistInteractionEntry>,
        optionCount: Int = 3
    ): String = buildString {
        // Roster artist header — only when the event maps to a known roster artist.
        if (artist != null) append("You are ${artist.name}, a ${artist.genre} music artist.\n\n")

        when (event) {
            is SimEvent.NeedUrgent -> {
                val topic = when (event.needType) {
                    NeedType.CREATIVE_FULFILLMENT -> "creative fulfillment"
                    NeedType.FINANCIAL_SECURITY   -> "financial security"
                    NeedType.RECOGNITION          -> "feeling recognized and visible"
                    NeedType.BELONGING            -> "feeling connected and belonging"
                    NeedType.AUTONOMY             -> "creative autonomy"
                }
                append("Write an email about your need for $topic — it's been neglected")
                if (event.currentValue < 0.20f) append(". This is now urgent")
                append(".\n\n")
            }
            is SimEvent.ContractExpiring -> {
                append("Write an email about your contract renewal. About ${event.daysRemaining} in-game days remain.\n\n")
                val balance = artist?.relationshipBalance ?: 0f
                when {
                    balance > 0.5f  -> append("The relationship has been strong — your tone is warm but pragmatic. ")
                    balance < -0.3f -> append("There have been real tensions. Tread carefully. ")
                }
            }
            is SimEvent.WantSurfaced -> {
                val want = event.wantType.name.lowercase(Locale.ROOT).replace('_', ' ')
                append("Write an email about your strong desire to $want.\n\n")
            }
            is SimEvent.RenewalOpened -> {
                append("Write an email opening contract renewal talks")
                if (event.round > 1) append(" (this is round ${event.round} of ongoing discussions)")
                append(".\n\n")
                val balance = artist?.relationshipBalance ?: 0f
                when {
                    balance > 0.5f  -> append("Your working relationship has been solid — approach this positively. ")
                    balance < -0.3f -> append("There have been real tensions. You want resolution but you're wary. ")
                    else            -> append("The relationship is professional. You want a fair deal. ")
                }
            }
            is SimEvent.NegotiationRound -> {
                val prospect = world.prospects[event.prospectId]
                val pName = prospect?.name ?: "an unsigned artist"
                val pGenre = prospect?.genre ?: "indie"
                append("You are $pName, a $pGenre artist in round ${event.round} of signing negotiations with a label.\n\n")
                val score = prospect?.signabilityScore ?: 0.5f
                when {
                    score >= 0.7f -> append("You're genuinely interested in this label. Write about what would seal the deal. ")
                    score <= 0.3f -> append("You have other offers and aren't easily convinced. Write skeptically about your terms. ")
                    else          -> append("You're curious but not yet committed. Write about what you need to feel confident. ")
                }
            }
            is SimEvent.DeadlineApproaching -> {
                val deadlineDesc = when (event.type) {
                    DeadlineType.ALBUM_RELEASE -> "album release"
                    DeadlineType.TOUR_BOOKING  -> "tour booking"
                    DeadlineType.PRESS_CYCLE   -> "press cycle"
                }
                val urgencyDesc = when {
                    event.ticksRemaining <= 5  -> "critically close — you need an answer now"
                    event.ticksRemaining <= 10 -> "coming up fast and you're getting anxious"
                    else                       -> "approaching and you want to get ahead of it"
                }
                append("Write an email about your $deadlineDesc deadline, which is $urgencyDesc (${event.ticksRemaining} days remaining).\n\n")
            }
            is SimEvent.DeadlineMissed -> {
                val deadlineDesc = when (event.type) {
                    DeadlineType.ALBUM_RELEASE -> "album release"
                    DeadlineType.TOUR_BOOKING  -> "tour booking"
                    DeadlineType.PRESS_CYCLE   -> "press cycle"
                }
                append("Write an email reacting to a missed $deadlineDesc deadline. Express how this affects you and what should happen now.\n\n")
            }
            is SimEvent.LabelNeedUrgent -> {
                append("You are a music industry business advisor writing to a label manager.\n\n")
                val issue = when (event.needType) {
                    LabelNeedType.CASH_FLOW       -> "the label's cash flow is under strain and needs immediate attention"
                    LabelNeedType.GENRE_DIVERSITY -> "the label's roster is too genre-concentrated and limiting growth"
                }
                append("Write a concise memo flagging that $issue.\n\n")
            }
            is SimEvent.CapabilityUnlockable -> {
                append("You are a music industry contact writing to a label manager.\n\n")
                val pitch = when (event.type) {
                    CapabilityType.PUBLICIST        -> "bringing on a dedicated publicist"
                    CapabilityType.IN_HOUSE_BOOKING -> "building an in-house booking operation"
                    CapabilityType.VIDEO_PRODUCTION -> "investing in video production capability"
                }
                append("Write an email pitching the idea of $pitch as the label's next strategic move.\n\n")
            }
            is SimEvent.RivalSigning -> {
                append("You are a music industry contact writing to a label manager.\n\n")
                val context = if (event.wasPlayerTarget) "a prospect your label was actively pursuing" else "a new signing from the unsigned pool"
                append("Write a brief message flagging that ${event.rivalName} just signed ${event.prospectName} (${event.genre}), $context.\n\n")
            }
            is SimEvent.RivalPoach -> {
                append("You are a music industry contact writing to a label manager.\n\n")
                append("Write a message informing them that ${event.artistName} has left to sign with ${event.rivalName}. Keep it factual and direct.\n\n")
            }
            is SimEvent.MarketShift -> {
                append("You are a music market analyst writing to a label manager.\n\n")
                val direction = if (event.currentTrend > event.previousTrend) "rising" else "declining"
                val delta = event.currentTrend - event.previousTrend
                val intensity = if (delta > 0.15f || delta < -0.15f) "significantly" else "noticeably"
                append("Write a brief market update: ${event.genre} is $intensity $direction (current trend: ${"%.2f".format(event.currentTrend)}).\n\n")
            }
            is SimEvent.IntelDrop -> {
                append("You are a music industry scout writing to a label manager.\n\n")
                append("Write a short message passing along this intel about ${event.genre}: \"${event.headline}\".\n\n")
            }
            is SimEvent.ScoutReport -> {
                append("You are a music scout filing a report for your label manager.\n\n")
                val prospect = world.prospects[event.prospectId]
                val pName = prospect?.name ?: "an unsigned artist"
                val pGenre = prospect?.genre ?: "indie"
                val score = prospect?.signabilityScore ?: 0.5f
                val tier = when {
                    score >= 0.7f -> "a standout find — genuinely buzzing right now"
                    score >= 0.4f -> "interesting but still developing"
                    else          -> "raw, but with potential worth tracking"
                }
                append("Write a scout report about $pName, a $pGenre artist. Your read: $tier.\n\n")
            }
            is SimEvent.CheckIn -> {
                append("Write a casual reply to your label manager who just reached out to check in on you.\n\n")
                val balance = artist?.relationshipBalance ?: 0f
                when {
                    balance > 0.3f  -> append("The relationship is solid — your tone is warm and genuine. ")
                    balance < -0.2f -> append("The relationship has been strained. Keep your reply brief and measured. ")
                    else            -> append("Be professional but real. ")
                }
                val lowestNeed = artist?.needs?.values?.minByOrNull { it.value }
                if (lowestNeed != null && lowestNeed.value < 0.3f) {
                    val topic = when (lowestNeed.type) {
                        NeedType.CREATIVE_FULFILLMENT -> "needing more creative space"
                        NeedType.FINANCIAL_SECURITY   -> "the financial situation being tight"
                        NeedType.RECOGNITION          -> "wanting more visibility and press coverage"
                        NeedType.BELONGING            -> "feeling a bit disconnected from the label"
                        NeedType.AUTONOMY             -> "wanting more control over creative direction"
                    }
                    append("Mention that $topic has been on your mind.\n\n")
                }
            }
            is SimEvent.LeadSurfaced,
            is SimEvent.SeasonEnded -> Unit
        }

        append("Provide exactly $optionCount response options.\n")

        // Artist dimensions — only for events with a matched roster artist.
        val dims = artist?.dimensions
        if (dims != null) {
            append("\nCommunication style: ")
            when {
                dims.confidence < 0.35f -> append("you hedge and second-guess yourself. ")
                dims.confidence > 0.65f -> append("you are direct and assertive. ")
                else                    -> append("you are measured and professional. ")
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

        // Interaction history — only for roster artist events; last 3 to keep prompt tight.
        if (history.isNotEmpty() && artist != null) {
            append("\n\nRecent history with this label:\n")
            history.takeLast(3).forEach { entry ->
                append("- ${entry.eventSummary}: label responded \"${entry.choiceMade}\"\n")
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
