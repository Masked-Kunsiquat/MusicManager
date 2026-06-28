package com.github.maskedkunisquat.musicmanager.data.repository

import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.data.mapper.eventSignature
import com.github.maskedkunisquat.musicmanager.data.mapper.toInboxItemOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toRelationshipDeltas
import com.github.maskedkunisquat.musicmanager.data.mapper.toTouchedArtistIds
import kotlin.math.abs
import com.github.maskedkunisquat.musicmanager.data.mapper.toTapeDeckItemOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toResponseEntity
import com.github.maskedkunisquat.musicmanager.data.mapper.toSimEventOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.TapeDeckItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistInteractionEntry
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.labelRenameCost
import com.github.maskedkunisquat.musicmanager.logic.sim.EntityRecord
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonFacts
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.sim.LabelIdentityEvaluator
import com.github.maskedkunisquat.musicmanager.logic.sim.NewSeasonInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.SeasonSummaryEvaluator
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.PASS_LEAD_COOLDOWN
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SimRepositoryImpl(
    private val dao: EventLogDao,
    private val engine: SimEngine,
    private val aiProvider: LabelAiProvider,
    seed: Long,
    private val saveWorld: (SimWorld) -> Unit = {},
    private val loadWorld: () -> SimWorld? = { null }
) : SimRepository {

    private val _loadedWorld: SimWorld? = loadWorld()
    override var world: SimWorld = _loadedWorld ?: WorldInitializer.initializeWorld(seed)
        private set
    private var _isWorldInitialized: Boolean = _loadedWorld != null
    override val isWorldInitialized: Boolean get() = _isWorldInitialized

    private val tickMutex = Mutex()
    private var startupChecksRan = false
    private val json = Json { ignoreUnknownKeys = true }

    // Must match EventGenerator.LEAD_SURFACE_CAP — controls how many leads can be on the deck.
    private companion object { const val MAX_SURFACED_LEADS = 3 }

    override fun observeUnresolved(): Flow<List<InboxItem>> =
        dao.observeUnresolved().map { entities -> entities.mapNotNull { it.toInboxItemOrNull() } }

    override fun observeActiveSurfacedLeads(): Flow<List<TapeDeckItem>> =
        dao.observeActiveSurfacedLeads().map { entities -> entities.mapNotNull { it.toTapeDeckItemOrNull() } }

    override suspend fun generateOptions(item: InboxItem): List<ResponseOption> {
        if (item.email.options.isNotEmpty()) return item.email.options
        // Options weren't stored (pre-migration row or serialization failure) — re-run inference.
        return aiProvider.generateEmail(item.event, world).options
    }

    // Returns the recordedAt timestamp of the SeasonEnded event for the given season number,
    // or null if no such event exists in the log.
    private fun seasonEndTimestamp(allEntities: List<EventLogEntity>, seasonNumber: Int): Long? =
        allEntities
            .filter { it.eventType == "season_ended" }
            .mapNotNull { entity ->
                runCatching {
                    val n = json.parseToJsonElement(entity.payload)
                        .jsonObject["seasonNumber"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: return@mapNotNull null
                    if (n == seasonNumber) entity.recordedAt else null
                }.getOrNull()
            }
            .maxOrNull()

    // Returns event log entities scoped to the current season only.
    // Anchors on the recordedAt of the previous SeasonEnded entity so that restarted seasons
    // (where seasonStartTick resets to 0) don't bleed prior-season events into current queries.
    private suspend fun currentSeasonEntities(): List<EventLogEntity> {
        val allEntities = dao.getAll()
        val prevSeasonNumber = world.season.seasonNumber - 1
        val seasonBoundary = if (prevSeasonNumber < 1) 0L else
            seasonEndTimestamp(allEntities, prevSeasonNumber) ?: 0L
        return if (seasonBoundary == 0L) allEntities
               else allEntities.filter { it.recordedAt > seasonBoundary }
    }

    // Returns event log entities scoped to the previous season (season N-1).
    // Returns empty list when currently in season 1 or no SeasonEnded event for N-1 is found.
    private suspend fun previousSeasonEntities(): List<EventLogEntity> {
        val prevSeasonNumber = world.season.seasonNumber - 1
        if (prevSeasonNumber < 1) return emptyList()
        val allEntities = dao.getAll()
        val end = seasonEndTimestamp(allEntities, prevSeasonNumber) ?: return emptyList()
        val start = if (prevSeasonNumber <= 1) 0L else
            seasonEndTimestamp(allEntities, prevSeasonNumber - 1) ?: 0L
        return allEntities.filter { it.recordedAt > start && it.recordedAt <= end }
    }

    // All world mutation goes through this; callers must already hold tickMutex.
    private suspend fun tickUnderLock() {
        // TODO: cache identity between ticks and invalidate only on response_applied writes;
        //  currently dao.getAll() is called on every tick (O(N) full scan).
        val identity = getLabelIdentity()
        aiProvider.onIdentityUpdated(identity)
        val result = engine.tick(world, identity)
        world = result.world
        // Build a signature set from currently-open events so the same (artist, need/want/contract)
        // doesn't flood the inbox across ticks while the player hasn't responded yet.
        val queued = dao.getUnresolved()
            .mapNotNull { it.toSimEventOrNull() }
            .map { it.eventSignature() }
            .toSet()
        result.events
            .filter { it.eventSignature() !in queued }
            .forEach { event ->
                val history = event.artistId?.let { getArtistHistory(it) } ?: emptyList()
                val email = aiProvider.generateEmail(event, world, history)
                dao.insert(event.toEntity(email))
            }
        // Persist world after events are in the DB. If killed between these two writes the world
        // snapshot is one tick behind — harmless, because the dedup guard prevents re-flooding.
        withContext(Dispatchers.IO) { saveWorld(world) }
    }

    override suspend fun tick() = tickMutex.withLock { tickUnderLock() }

    // Re-derives ArtistState.relationshipBalance from the event log and corrects any divergence.
    // No-op when the world snapshot is current; only corrects pre-3-D saves where the field
    // defaulted to 0f despite accumulated RelationshipChange and WantSatisfied effects.
    private suspend fun reconcileRelationshipBalancesUnderLock() {
        val derived = mutableMapOf<String, Float>()
        for (entity in dao.getResponseEntities()) {
            entity.toRelationshipDeltas().forEach { (id, delta) ->
                derived[id] = (derived[id] ?: 0f) + delta
            }
        }
        val corrections = world.artists.entries.mapNotNull { (id, artist) ->
            val expected = derived[id] ?: 0f
            if (abs(expected - artist.relationshipBalance) > 0.001f) id to artist.copy(relationshipBalance = expected)
            else null
        }.toMap()
        if (corrections.isNotEmpty()) {
            world = world.copy(artists = world.artists + corrections)
            withContext(Dispatchers.IO) { saveWorld(world) }
        }
    }

    // Extracts SeasonFacts from a set of EventLogEntity rows for SeasonSummaryEvaluator.
    // Parses rival_poach events and response_applied payloads — all JSON parsing stays in core-data.
    private fun extractSeasonFacts(entities: List<EventLogEntity>): SeasonFacts {
        val poachIds = mutableSetOf<String>()
        val walkedIds = mutableSetOf<String>()
        var met = 0
        var missed = 0
        for (entity in entities) {
            when (entity.eventType) {
                "rival_poach" -> {
                    runCatching {
                        val obj = json.parseToJsonElement(entity.payload).jsonObject
                        obj["artistId"]?.jsonPrimitive?.content?.let { poachIds.add(it) }
                    }
                }
                "deadline_missed" -> missed++
                "response_applied" -> {
                    runCatching {
                        val effects = json.parseToJsonElement(entity.payload)
                            .jsonObject["effects"]?.jsonArray ?: return@runCatching
                        for (e in effects) {
                            val obj = e.jsonObject
                            when (obj["type"]?.jsonPrimitive?.content) {
                                "renewal_walked" ->
                                    obj["artistId"]?.jsonPrimitive?.content?.let { walkedIds.add(it) }
                                "meet_deadline" -> met++
                            }
                        }
                    }
                }
            }
        }
        val lostIds = poachIds + walkedIds
        val departedNames = lostIds.mapNotNull { id -> world.artists[id]?.name }.sorted()
        return SeasonFacts(
            rivalPoachArtistIds = poachIds,
            renewalWalkedArtistIds = walkedIds,
            deadlinesMet = met,
            deadlinesMissed = missed,
            departedArtistNames = departedNames
        )
    }

    // Back-fills ArtistState.lastInteractionDay for saves predating 4-D (where it defaulted to 0).
    private suspend fun reconcileLastInteractionDaysUnderLock() {
        val latestDay = mutableMapOf<String, Int>()
        for (entity in dao.getResponseEntities()) {
            val day = entity.dayOfGame
            for (artistId in entity.toTouchedArtistIds()) {
                if ((latestDay[artistId] ?: -1) < day) latestDay[artistId] = day
            }
        }
        val corrections = world.artists.entries.mapNotNull { (id, artist) ->
            val expected = latestDay[id] ?: return@mapNotNull null
            if (expected > artist.lastInteractionDay) id to artist.copy(lastInteractionDay = expected)
            else null
        }.toMap()
        if (corrections.isNotEmpty()) {
            world = world.copy(artists = world.artists + corrections)
            withContext(Dispatchers.IO) { saveWorld(world) }
        }
    }

    override suspend fun initializeIfEmpty(days: Int) = tickMutex.withLock {
        runStartupChecksUnderLock()
        if (dao.getAll().isEmpty()) {
            populateInboxUnderLock(days)
        }
    }

    override suspend fun initializeWorld(labelName: String, days: Int) = tickMutex.withLock {
        val trimmed = labelName.trim().ifBlank { "Unnamed Label" }
        world = world.copy(label = world.label.copy(name = trimmed))
        withContext(Dispatchers.IO) { saveWorld(world) }
        _isWorldInitialized = true
        runStartupChecksUnderLock()
        populateInboxUnderLock(days)
    }

    override suspend fun renameLabel(name: String) = tickMutex.withLock {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withLock
        val cost = labelRenameCost(world.label)
        if (world.label.funds < cost) return@withLock
        world = world.copy(
            label = world.label.copy(
                name = trimmed,
                funds = world.label.funds - cost
            )
        )
        withContext(Dispatchers.IO) { saveWorld(world) }
    }

    private suspend fun runStartupChecksUnderLock() {
        if (!startupChecksRan) {
            reconcileRelationshipBalancesUnderLock()
            reconcileLastInteractionDaysUnderLock()
            if (!dao.verifyChain()) {
                android.util.Log.w("SimRepository", "Event log hash chain integrity check failed — possible DB tampering")
            }
            startupChecksRan = true
        }
    }

    // Tick until we have at least `days` inbox events, capped at 90 ticks to prevent
    // infinite loops on pathological seeds. `days` here means target event count.
    private suspend fun populateInboxUnderLock(days: Int) {
        var ticks = 0
        while (dao.getAll().size < days && ticks < 90) {
            tickUnderLock()
            ticks++
        }
    }

    override suspend fun markViewed(eventId: String) {
        dao.markViewed(eventId, System.currentTimeMillis())
    }

    override fun observeUnresolvedSeasonEnd(): Flow<Boolean> =
        dao.observeUnresolvedSeasonEnd().map { it.isNotEmpty() }

    override suspend fun getSeasonSummary(): SeasonSummary {
        val entities = currentSeasonEntities()
        val facts = extractSeasonFacts(entities)
        return SeasonSummaryEvaluator.evaluate(world, facts)
    }

    override suspend fun getLabelIdentity(): LabelIdentity {
        val entities = currentSeasonEntities().map { EntityRecord(it.id, it.eventType, it.payload) }
        val actions = LabelIdentityEvaluator.extractGenreActions(entities, world)
        val rosterArtists = world.label.rosterIds.mapNotNull { world.artists[it] }
        return LabelIdentityEvaluator.evaluate(actions, rosterArtists)
    }

    override suspend fun getPreviousSeasonPrimaryGenre(): String? {
        val entities = previousSeasonEntities()
        if (entities.isEmpty()) return null
        val records = entities.map { EntityRecord(it.id, it.eventType, it.payload) }
        val actions = LabelIdentityEvaluator.extractGenreActions(records, world)
        // Pass empty artist list: only primaryGenre is used by callers; aesthetic (roster-driven)
        // would be wrong here since world.artists reflects the current season.
        return LabelIdentityEvaluator.evaluate(actions, emptyList()).primaryGenre
    }

    override suspend fun getArtistHistory(artistId: String): List<ArtistInteractionEntry> {
        // Escape SQLite LIKE wildcards so "signed_" prefixes don't match unintended rows.
        val escapedId = artistId.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val resolved = dao.getResolvedForArtist(escapedId)
        if (resolved.isEmpty()) return emptyList()
        // Build lookup from originalEventId → optionText from response_applied rows.
        val optionTextByOriginalId = buildMap<String, String> {
            for (entity in dao.getResponseEntities()) {
                runCatching {
                    val obj = json.parseToJsonElement(entity.payload).jsonObject
                    val origId = obj["originalEventId"]?.jsonPrimitive?.content ?: return@runCatching
                    val text = obj["optionText"]?.jsonPrimitive?.content ?: return@runCatching
                    put(origId, text)
                }
            }
        }
        return resolved.mapNotNull { event ->
            val subject = event.emailSubject.ifBlank { return@mapNotNull null }
            val choice = optionTextByOriginalId[event.id] ?: return@mapNotNull null
            ArtistInteractionEntry(day = event.dayOfGame, eventSummary = subject, choiceMade = choice)
        }.sortedBy { it.day }.takeLast(10)
    }

    override suspend fun getGenreTrendHistory(): Map<String, List<Float>> {
        val events = dao.getMarketShiftEvents()
            .mapNotNull { it.toSimEventOrNull() as? SimEvent.MarketShift }
            .sortedBy { it.dayOfGame }
        if (events.isEmpty()) return emptyMap()
        val byGenre = mutableMapOf<String, MutableList<Float>>()
        for (event in events) {
            val list = byGenre.getOrPut(event.genre) { mutableListOf() }
            if (list.isEmpty()) list.add(event.previousTrend)
            list.add(event.currentTrend)
        }
        return byGenre
    }

    override suspend fun startNewSeason() = tickMutex.withLock {
        val seasonEndEntity = dao.getUnresolvedSeasonEnd().firstOrNull() ?: return@withLock
        val now = System.currentTimeMillis()
        // Guard against double-advance: if the world was already saved for this season (e.g.,
        // saveWorld succeeded but markResolved crashed), skip the advance but always markResolved.
        val eventSeasonNumber = runCatching {
            json.parseToJsonElement(seasonEndEntity.payload)
                .jsonObject["seasonNumber"]?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()
        if (eventSeasonNumber == null || world.season.seasonNumber < eventSeasonNumber) {
            world = NewSeasonInitializer.advance(world)
            withContext(Dispatchers.IO) { saveWorld(world) }
        }
        dao.markResolved(seasonEndEntity.id, "season_advanced", now)
    }

    override suspend fun checkInWithArtist(artistId: String) = tickMutex.withLock {
        if (artistId !in world.label.rosterIds) return@withLock
        val artist = world.artists[artistId] ?: return@withLock
        if (world.currentDay - artist.lastInteractionDay < SimEvent.CheckIn.COOLDOWN_TICKS) return@withLock
        val sig = "check_in:$artistId"
        val hasPending = dao.getUnresolved()
            .mapNotNull { it.toSimEventOrNull() }
            .any { it.eventSignature() == sig }
        if (hasPending) return@withLock
        val event = SimEvent.CheckIn(artistId, world.currentDay)
        val history = getArtistHistory(artistId)
        val email = aiProvider.generateEmail(event, world, history)
        dao.insert(event.toEntity(email))
    }

    override suspend fun requestLead(prospectId: String) = tickMutex.withLock {
        if (prospectId !in world.prospects) return@withLock
        if (prospectId in world.surfacedLeads) return@withLock
        if (prospectId in world.unavailableProspects) return@withLock
        if (prospectId in world.activeNegotiations) return@withLock
        if (world.passedLeads[prospectId]?.let { world.currentDay - it < PASS_LEAD_COOLDOWN } == true) return@withLock
        if (world.surfacedLeads.size >= MAX_SURFACED_LEADS) return@withLock
        val event = SimEvent.LeadSurfaced(prospectId, world.currentDay)
        // Generate email before mutating world so a failure leaves state unchanged.
        val email = aiProvider.generateEmail(event, world)
        world = world.copy(surfacedLeads = world.surfacedLeads + prospectId)
        withContext(Dispatchers.IO) { saveWorld(world) }
        dao.insert(event.toEntity(email))
    }

    override suspend fun resolveEvent(eventId: String, option: ResponseOption) = tickMutex.withLock {
        val (newWorld, injectedEvents) = applyResponse(world, option)
        world = newWorld
        // Persist world before touching the DAO. If the process dies between here and
        // markResolved the event re-appears as unresolved on next launch, which is
        // preferable to losing the world effects with the event already marked gone.
        withContext(Dispatchers.IO) { saveWorld(world) }
        val now = System.currentTimeMillis()
        // Pre-compute entities outside the transaction — AI inference must not run inside it.
        val responseEntity = option.toResponseEntity(eventId, world.currentDay)
        // responseEntity isn't in the DB yet when getArtistHistory() runs, so follow-up emails
        // would miss the choice that triggered them. Synthesize an entry and prepend it for
        // any follow-up that targets the same artist as the original event.
        val originalEvent = dao.getById(eventId)
        val originalArtistId = originalEvent?.toSimEventOrNull()?.artistId
        val currentChoiceEntry = if (originalEvent != null && originalArtistId != null) {
            val subject = originalEvent.emailSubject.ifBlank { null }
            if (subject != null) ArtistInteractionEntry(
                day = world.currentDay,
                eventSummary = subject,
                choiceMade = option.text
            ) else null
        } else null
        val injectedEntities = injectedEvents.map { event ->
            val baseHistory = event.artistId?.let { getArtistHistory(it) } ?: emptyList()
            val history = if (currentChoiceEntry != null && event.artistId == originalArtistId) {
                baseHistory + currentChoiceEntry
            } else {
                baseHistory
            }
            event.toEntity(aiProvider.generateEmail(event, world, history))
        }
        dao.resolveWithFollowUps(eventId, option.id, now, responseEntity, injectedEntities)
    }
}
