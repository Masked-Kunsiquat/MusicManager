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

    override fun observeUnresolved(): Flow<List<InboxItem>> =
        dao.observeUnresolved().map { entities -> entities.mapNotNull { it.toInboxItemOrNull() } }

    override fun generateOptions(item: InboxItem): List<ResponseOption> =
        aiProvider.generateEmail(item.event, world).options

    override suspend fun tick() {
        val result = engine.tick(world)
        world = result.world
        result.events.forEach { event ->
            val email = aiProvider.generateEmail(event, world)
            dao.insert(event.toEntity(email))
        }
    }

    override suspend fun initializeIfEmpty(days: Int) {
        if (dao.getAll().isEmpty()) {
            repeat(days) { tick() }
        }
    }

    override suspend fun resolveEvent(eventId: String, option: ResponseOption) {
        world = applyResponse(world, option)
        dao.markResolved(eventId, option.id, System.currentTimeMillis())
    }
}
