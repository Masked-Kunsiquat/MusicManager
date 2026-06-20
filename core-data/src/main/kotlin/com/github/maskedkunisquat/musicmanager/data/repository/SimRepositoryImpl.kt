package com.github.maskedkunisquat.musicmanager.data.repository

import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import com.github.maskedkunisquat.musicmanager.data.mapper.eventSignature
import com.github.maskedkunisquat.musicmanager.data.mapper.toInboxItemOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toResponseEntity
import com.github.maskedkunisquat.musicmanager.data.mapper.toSimEventOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    override fun observeUnresolved(): Flow<List<InboxItem>> =
        dao.observeUnresolved().map { entities -> entities.mapNotNull { it.toInboxItemOrNull() } }

    override suspend fun generateOptions(item: InboxItem): List<ResponseOption> {
        if (item.email.options.isNotEmpty()) return item.email.options
        // Options weren't stored (pre-migration row or serialization failure) — re-run inference.
        return aiProvider.generateEmail(item.event, world).options
    }

    // All world mutation goes through this; callers must already hold tickMutex.
    private suspend fun tickUnderLock() {
        val result = engine.tick(world)
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

    override suspend fun initializeIfEmpty(days: Int) = tickMutex.withLock {
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
