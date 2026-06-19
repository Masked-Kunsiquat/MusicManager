package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

class SimEngine {

    fun tick(world: SimWorld): TickResult {
        // Seed from world.seed xor currentDay so each tick is deterministic but unique.
        val rng = Random(world.seed xor world.currentDay.toLong())
        val nextWorld = world.copy(
            currentDay = world.currentDay + 1,
            artists = world.artists.mapValues { (_, artist) -> decayNeeds(artist) },
            market = tickMarket(world.market, rng)
        )
        return TickResult(world = nextWorld, events = generateEvents(nextWorld))
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
