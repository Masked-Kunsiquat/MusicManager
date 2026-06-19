package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

class SimEngine {

    fun tick(world: SimWorld): TickResult {
        // Two separate RNGs per tick, both seed-deterministic:
        // marketRng drives trend drift; eventRng drives probabilistic event emission.
        // Using currentDay vs currentDay+1 as XOR operands keeps them independent.
        val marketRng = Random(world.seed xor world.currentDay.toLong())
        val previousMarket = world.market
        val nextWorld = world.copy(
            currentDay = world.currentDay + 1,
            artists = world.artists.mapValues { (_, artist) -> decayNeeds(artist) },
            market = tickMarket(world.market, marketRng)
        )
        val eventRng = Random(world.seed xor nextWorld.currentDay.toLong())
        return TickResult(world = nextWorld, events = generateEvents(nextWorld, previousMarket, eventRng))
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
