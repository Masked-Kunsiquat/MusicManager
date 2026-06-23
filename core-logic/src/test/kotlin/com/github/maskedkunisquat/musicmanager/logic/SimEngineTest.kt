package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimEngineTest {

    private val engine = SimEngine()

    @Test
    fun `tick advances the day by one`() {
        val world = WorldInitializer.initializeWorld(1L)
        assertEquals(1, engine.tick(world).world.currentDay)
    }

    @Test
    fun `needs decay on every tick`() {
        val world = WorldInitializer.initializeWorld(1L)
        val after = engine.tick(world).world
        for (artistId in world.artists.keys) {
            val before = world.artists[artistId]!!
            val updated = after.artists[artistId]!!
            for (needType in before.needs.keys) {
                assertTrue(
                    "$needType did not decay for ${before.name}",
                    updated.needs[needType]!!.value <= before.needs[needType]!!.value
                )
            }
        }
    }

    @Test
    fun `tickN is deterministic for the same seed`() {
        val seed = 55L
        val (world1, events1) = engine.tickN(WorldInitializer.initializeWorld(seed), 20)
        val (world2, events2) = engine.tickN(WorldInitializer.initializeWorld(seed), 20)
        assertEquals(world1, world2)
        assertEquals(events1, events2)
    }

    @Test
    fun `urgent need events are emitted after enough ticks`() {
        val (_, events) = engine.tickN(WorldInitializer.initializeWorld(1L), 60)
        assertTrue(
            "Expected NeedUrgent events after 60 ticks",
            events.any { it is SimEvent.NeedUrgent }
        )
    }

    @Test
    fun `contract expiry events fire when approaching expiry`() {
        // Contracts expire in 60-90 ticks; tick to exactly the 20-tick warning threshold
        val world = WorldInitializer.initializeWorld(3L)
        val shortestExpiry = world.contracts.values.minOf { it.expiryDay }
        val ticksToWarning = (shortestExpiry - 20).coerceAtLeast(1)
        val (_, events) = engine.tickN(world, ticksToWarning)
        assertTrue(
            "Expected ContractExpiring event at 20-tick threshold",
            events.any { it is SimEvent.ContractExpiring && it.daysRemaining == 20 }
        )
    }
}
