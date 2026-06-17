package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0HarnessTest {

    @Test
    fun `phase 0 done-when - init world, tick 60 days, every event has at least 2 response options`() {
        val seed = 42L
        val engine = SimEngine()
        val ai = StubAiProvider()

        val initialWorld = WorldInitializer.initializeWorld(seed)
        val (finalWorld, events) = engine.tickN(initialWorld, 60)

        assertTrue("No events generated in 60 ticks — needs decay math may be broken", events.isNotEmpty())

        for (event in events) {
            val options = ai.generateResponseOptions(event, finalWorld)
            assertTrue(
                "Event ${event::class.simpleName} produced ${options.size} option(s), expected ≥ 2",
                options.size >= 2
            )
        }

        // Print harness output for manual inspection during development
        println("=== Phase 0 Harness: seed=$seed, 60 ticks ===")
        println("Roster: ${initialWorld.artists.values.map { "${it.name} (${it.genre})" }}")
        println("Events: ${events.size} total")
        events.take(5).forEach { event ->
            println("  [day ${event.dayOfGame}] ${event::class.simpleName}")
            ai.generateResponseOptions(event, finalWorld).forEach { opt ->
                println("    > ${opt.text}")
            }
        }
    }
}
