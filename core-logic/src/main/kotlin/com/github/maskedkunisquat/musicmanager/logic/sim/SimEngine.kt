package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

class SimEngine {

    fun tick(world: SimWorld): TickResult {
        val nextWorld = world.copy(
            currentDay = world.currentDay + 1,
            artists = world.artists.mapValues { (_, artist) -> decayNeeds(artist) }
        )
        return TickResult(world = nextWorld, events = generateEvents(nextWorld))
    }

    fun tickN(world: SimWorld, ticks: Int): Pair<SimWorld, List<SimEvent>> {
        var current = world
        val allEvents = mutableListOf<SimEvent>()
        repeat(ticks) {
            val result = tick(current)
            current = result.world
            allEvents += result.events
        }
        return current to allEvents
    }
}
