package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

class SimEngine {

    fun tick(world: SimWorld): TickResult {
        // Three independent seed-deterministic RNGs per tick. XOR operands differ by +0/+1/+2
        // from currentDay, which is sufficient since these are seeds (not offsets) into Random.
        val nextDay = world.currentDay + 1
        val marketRng = Random(world.seed xor world.currentDay.toLong())
        val scoutRng  = Random(world.seed xor (world.currentDay.toLong() + 2L))
        val eventRng  = Random(world.seed xor nextDay.toLong())

        val previousMarket = world.market
        val (updatedScouts, scoutReports) = tickScouts(world.scouts, nextDay, world.prospects, scoutRng)
        val newMarket = tickMarket(world.market, marketRng)
        val nextWorld = world.copy(
            currentDay = nextDay,
            artists = world.artists.mapValues { (_, artist) -> decayNeeds(artist) },
            market = newMarket,
            scouts = updatedScouts,
            chartSnapshot = if (nextDay % 3 == 0) newMarket else world.chartSnapshot
        )
        val events = generateEvents(nextWorld, previousMarket, eventRng) + scoutReports
        val capabilityEvents = events.filterIsInstance<SimEvent.CapabilityUnlockable>()
        val finalWorld = if (capabilityEvents.isEmpty()) nextWorld else nextWorld.copy(
            capabilityNoticedAt = nextWorld.capabilityNoticedAt +
                capabilityEvents.associate { it.type.name to nextWorld.currentDay }
        )
        return TickResult(world = finalWorld, events = events)
    }

    fun tickN(world: SimWorld, ticks: Int): TickResult {
        var current = world
        val allEvents = mutableListOf<SimEvent>()
        repeat(ticks) {
            val result = tick(current)
            current = result.world
            allEvents += result.events
        }
        return TickResult(world = current, events = allEvents)
    }
}
