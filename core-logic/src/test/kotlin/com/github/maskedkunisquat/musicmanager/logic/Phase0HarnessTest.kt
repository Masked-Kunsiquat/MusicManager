package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import kotlinx.coroutines.runBlocking
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
            // LeadSurfaced goes to TapeDeckScreen, not the inbox — no subject/body expected.
            if (event is SimEvent.LeadSurfaced) continue
            val email = runBlocking { ai.generateEmail(event, finalWorld) }
            assertTrue(
                "Event ${event::class.simpleName} produced ${email.options.size} option(s), expected ≥ 2",
                email.options.size >= 2
            )
            assertTrue(
                "Event ${event::class.simpleName} produced empty subject",
                email.subject.isNotBlank()
            )
            assertTrue(
                "Event ${event::class.simpleName} produced empty body",
                email.body.isNotBlank()
            )
        }

    }
}
