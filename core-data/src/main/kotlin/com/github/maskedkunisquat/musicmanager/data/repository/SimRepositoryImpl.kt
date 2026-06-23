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
import com.github.maskedkunisquat.musicmanager.logic.model.GenreAction
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonFacts
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.sim.LabelIdentityEvaluator
import com.github.maskedkunisquat.musicmanager.logic.sim.NewSeasonInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.SIGNED_ARTIST_ID_PREFIX
import com.github.maskedkunisquat.musicmanager.logic.sim.SeasonSummaryEvaluator
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
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

    override var world: SimWorld = loadWorld() ?: WorldInitializer.initializeWorld(seed)
        private set

    private val tickMutex = Mutex()
    private var startupChecksRan = false
    private val json = Json { ignoreUnknownKeys = true }

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
                // Phase 2: pass the world snapshot at the time of the event, not the post-tick world.
                // Needs/dimensions don't tick yet so this is benign in Phase 1.
                val email = aiProvider.generateEmail(event, world)
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
        if (!startupChecksRan) {
            reconcileRelationshipBalancesUnderLock()
            reconcileLastInteractionDaysUnderLock()
            if (!dao.verifyChain()) {
                android.util.Log.w("SimRepository", "Event log hash chain integrity check failed — possible DB tampering")
            }
            startupChecksRan = true
        }
        if (dao.getAll().isEmpty()) {
            // Tick until we have at least `days` inbox events, capped at 90 ticks to prevent
            // infinite loops on pathological seeds. `days` here means target event count.
            var ticks = 0
            while (dao.getAll().size < days && ticks < 90) {
                tickUnderLock()
                ticks++
            }
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
        val entities = currentSeasonEntities()
        val actions = extractGenreActions(entities)
        return LabelIdentityEvaluator.evaluate(actions, world.artists.values)
    }

    override suspend fun getPreviousSeasonPrimaryGenre(): String? {
        val entities = previousSeasonEntities()
        if (entities.isEmpty()) return null
        val actions = extractGenreActions(entities)
        // Pass empty artist list: only primaryGenre is used by callers; aesthetic (roster-driven)
        // would be wrong here since world.artists reflects the current season.
        return LabelIdentityEvaluator.evaluate(actions, emptyList()).primaryGenre
    }

    // Extracts GenreAction list from current-season response_applied entities.
    // Genre is resolved by looking up prospectId/artistId in the current world; unknown IDs
    // are skipped (may occur when old-season prospects have left the pool after a season advance).
    private fun extractGenreActions(entities: List<EventLogEntity>): List<GenreAction> {
        val result = mutableListOf<GenreAction>()
        for (entity in entities) {
            if (entity.eventType != "response_applied") continue
            runCatching {
                val effects = json.parseToJsonElement(entity.payload)
                    .jsonObject["effects"]?.jsonArray ?: return@runCatching
                for (e in effects) {
                    val obj = e.jsonObject
                    val action: GenreAction? = when (obj["type"]?.jsonPrimitive?.content) {
                        "pursue_lead" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveProspectGenre(it) }
                            ?.let { GenreAction(it, +0.05f) }
                        "pass_lead" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveProspectGenre(it) }
                            ?.let { GenreAction(it, -0.03f) }
                        "sign_artist" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveSignedGenre(it) }
                            ?.let { GenreAction(it, +0.10f) }
                        else -> null
                    }
                    action?.let { result += it }
                }
            }
        }
        return result
    }

    // Prospects stay in world.prospects even after being pursued/passed, so the lookup is valid
    // within a season. If the prospect was signed, the new artist carries the same genre.
    private fun resolveProspectGenre(prospectId: String): String? =
        world.prospects[prospectId]?.genre
            ?: world.artists["$SIGNED_ARTIST_ID_PREFIX$prospectId"]?.genre

    private fun resolveSignedGenre(prospectId: String): String? =
        world.artists["$SIGNED_ARTIST_ID_PREFIX$prospectId"]?.genre
            ?: world.prospects[prospectId]?.genre

    override suspend fun getArtistHistory(artistId: String): List<ArtistInteractionEntry> {
        val resolved = dao.getResolvedForArtist(artistId)
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
        }.takeLast(10)
    }

    override suspend fun getGenreTrendHistory(): Map<String, List<Float>> {
        val events = dao.getMarketShiftEvents()
            .mapNotNull { it.toSimEventOrNull() as? SimEvent.MarketShift }
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
        val injectedEntities = injectedEvents.map { event ->
            event.toEntity(aiProvider.generateEmail(event, world))
        }
        dao.resolveWithFollowUps(eventId, option.id, now, responseEntity, injectedEntities)
    }
}
