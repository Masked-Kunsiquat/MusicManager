package com.github.maskedkunisquat.musicmanager.data.repository

import com.github.maskedkunisquat.musicmanager.data.dao.EventLogDao
import com.github.maskedkunisquat.musicmanager.data.mapper.toInboxItemOrNull
import com.github.maskedkunisquat.musicmanager.data.mapper.toEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.LabelAiProvider
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.SimRepository
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimRepositoryImpl(
    private val dao: EventLogDao,
    private val engine: SimEngine,
    private val aiProvider: LabelAiProvider,
    seed: Long
) : SimRepository {

    // World is in-memory for Phase 1. On process restart it re-initializes from seed.
    // Phase 2: persist world snapshot and restore on cold start.
    override var world: SimWorld = WorldInitializer.initializeWorld(seed)
        private set

    private val tickMutex = Mutex()

    override fun observeUnresolved(): Flow<List<InboxItem>> =
        dao.observeUnresolved().map { entities -> entities.mapNotNull { it.toInboxItemOrNull() } }

    // Gemma writes option labels; stub owns the underlying effects/costs.
    // The delay here is intentional — Gemma is generating the text the player sees.
    override suspend fun generateOptions(item: InboxItem): List<ResponseOption> =
        aiProvider.generateEmail(item.event, world).options

    // All world mutation goes through this; callers must already hold tickMutex.
    private suspend fun tickUnderLock() {
        val result = engine.tick(world)
        world = result.world
        result.events.forEach { event ->
            // Phase 2: pass the world snapshot at the time of the event, not the post-tick world.
            // Needs/dimensions don't tick yet so this is benign in Phase 1.
            val email = aiProvider.generateEmail(event, world)
            dao.insert(event.toEntity(email))
        }
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

    override suspend fun resolveEvent(eventId: String, option: ResponseOption) = tickMutex.withLock {
        world = applyResponse(world, option)
        dao.markResolved(eventId, option.id, System.currentTimeMillis())
    }
}
