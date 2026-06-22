package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.DemoState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapeDeckTest {

    private fun prospect(
        id: String = "p0",
        genre: String = "indie-rock"
    ) = ProspectState(
        id = id,
        name = "Prospect $id",
        genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        signabilityScore = 0.5f,
        demo = DemoState(descriptor = "lo-fi bedroom pop", rawScore = 0.5f, submittedDay = 0)
    )

    private fun world(
        currentDay: Int = 12,   // 12 % 3 == 0 -> lead surface day
        surfacedLeads: Set<String> = emptySet(),
        passedLeads: Map<String, Int> = emptyMap(),
        watchedLeads: Map<String, Int> = emptyMap(),
        activeNegotiations: Map<String, Int> = emptyMap(),
        unavailableProspects: Set<String> = emptySet(),
        extraProspects: Map<String, ProspectState> = emptyMap(),
        tasteVector: Map<String, Float> = mapOf("indie-rock" to 0.5f, "pop" to 0.5f)
    ): SimWorld {
        val base = prospect("p0", "indie-rock")
        val all = mapOf("p0" to base) + extraProspects
        return SimWorld(
            seed = 1L,
            currentDay = currentDay,
            artists = emptyMap(),
            label = LabelState(
                funds = 10_000_000L,
                reputation = emptyMap(),
                rosterIds = emptySet(),
                tasteVector = tasteVector
            ),
            market = MarketState(mapOf("indie-rock" to 0.5f, "pop" to 0.5f)),
            contracts = emptyMap(),
            prospects = all,
            surfacedLeads = surfacedLeads,
            passedLeads = passedLeads,
            watchedLeads = watchedLeads,
            activeNegotiations = activeNegotiations,
            unavailableProspects = unavailableProspects
        )
    }

    // --- Taste vector ---

    @Test
    fun `PursueLead increments tasteVector for prospect genre by 0_05`() {
        val w = world()
        val option = ResponseOption("id", "Pursue", listOf(StateEffect.PursueLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(0.55f, newWorld.label.tasteVector["indie-rock"] ?: 0f, 0.001f)
    }

    @Test
    fun `PassLead decrements tasteVector for prospect genre by 0_03`() {
        val w = world(surfacedLeads = setOf("p0"))
        val option = ResponseOption("id", "Pass", listOf(StateEffect.PassLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(0.47f, newWorld.label.tasteVector["indie-rock"] ?: 0f, 0.001f)
    }

    @Test
    fun `tasteVector clamped to 1_0 on repeated pursues`() {
        var w = world(tasteVector = mapOf("indie-rock" to 0.98f))
        repeat(3) {
            val option = ResponseOption("id", "Pursue", listOf(StateEffect.PursueLead("p0")))
            val (newW, _) = applyResponse(w, option)
            w = newW
        }
        assertEquals(1.0f, w.label.tasteVector["indie-rock"] ?: 0f, 0.001f)
    }

    // --- Passed lead cooldown ---

    @Test
    fun `passed lead not re-surfaced within 10-tick cooldown`() {
        // currentDay=12, passedLeads[p0]=7 → gap=5 < 10 → still hidden
        val w = world(currentDay = 12, passedLeads = mapOf("p0" to 7))
        assertTrue("Expected no LeadSurfaced for recently-passed lead",
            generateEvents(w).none { it is SimEvent.LeadSurfaced && (it as SimEvent.LeadSurfaced).prospectId == "p0" }
        )
    }

    @Test
    fun `passed lead re-surfaces once 10-tick cooldown expires`() {
        // currentDay=12, passedLeads[p0]=2 → gap=10 >= 10 → re-surface
        val w = world(currentDay = 12, passedLeads = mapOf("p0" to 2))
        assertNotNull("Expected LeadSurfaced after cooldown",
            generateEvents(w).filterIsInstance<SimEvent.LeadSurfaced>().firstOrNull { it.prospectId == "p0" }
        )
    }

    @Test
    fun `PassLead stamps passedLeads on world`() {
        val w = world(surfacedLeads = setOf("p0"))
        val option = ResponseOption("id", "Pass", listOf(StateEffect.PassLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(w.currentDay, newWorld.passedLeads["p0"])
    }

    // --- Watched lead resurface ---

    @Test
    fun `watched lead not immediately re-surfaced`() {
        // currentDay=12, watchedLeads[p0]=10 → gap=2 < 5 → still waiting
        val w = world(currentDay = 12, watchedLeads = mapOf("p0" to 10))
        assertTrue("Expected no LeadSurfaced for recently-watched lead",
            generateEvents(w).none { it is SimEvent.LeadSurfaced && (it as SimEvent.LeadSurfaced).prospectId == "p0" }
        )
    }

    @Test
    fun `watched lead re-surfaces after 5-tick wait`() {
        // currentDay=12, watchedLeads[p0]=7 → gap=5 >= 5 → re-surface
        val w = world(currentDay = 12, watchedLeads = mapOf("p0" to 7))
        assertNotNull("Expected LeadSurfaced after 5-tick watch wait",
            generateEvents(w).filterIsInstance<SimEvent.LeadSurfaced>().firstOrNull { it.prospectId == "p0" }
        )
    }

    @Test
    fun `WatchLead applies rawScore drift to prospect`() {
        val w = world(surfacedLeads = setOf("p0"))
        val originalScore = w.prospects["p0"]!!.demo.rawScore
        val option = ResponseOption("id", "Watch", listOf(StateEffect.WatchLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        val newScore = newWorld.prospects["p0"]!!.demo.rawScore
        // Score should differ by at most ±0.05 (allow 1e-6f epsilon for float rounding).
        assertTrue("rawScore drift exceeds ±0.05", kotlin.math.abs(newScore - originalScore) <= 0.05f + 1e-6f)
    }

    @Test
    fun `WatchLead stamps watchedLeads and removes from surfacedLeads`() {
        val w = world(surfacedLeads = setOf("p0"))
        val option = ResponseOption("id", "Watch", listOf(StateEffect.WatchLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        assertEquals(w.currentDay, newWorld.watchedLeads["p0"])
        assertTrue("p0 should be removed from surfacedLeads", "p0" !in newWorld.surfacedLeads)
    }

    // --- Cap of 3 concurrent leads ---

    @Test
    fun `LeadSurfaced capped at 3 concurrent leads`() {
        val extras = (1..8).associate { i ->
            "p$i" to prospect("p$i", "pop")
        }
        val w = world(
            currentDay = 12,
            surfacedLeads = setOf("p0", "p1", "p2"),
            extraProspects = extras
        )
        assertTrue("Expected no LeadSurfaced when 3 already surfaced",
            generateEvents(w).none { it is SimEvent.LeadSurfaced }
        )
    }

    @Test
    fun `LeadSurfaced surfaces up to cap when below limit`() {
        val extras = (1..8).associate { i -> "p$i" to prospect("p$i", "pop") }
        val w = world(
            currentDay = 12,
            surfacedLeads = setOf("p0"),  // 1 surfaced, cap=3, so 2 more can surface
            extraProspects = extras
        )
        val surfaced = generateEvents(w).filterIsInstance<SimEvent.LeadSurfaced>()
        assertTrue("Expected 1-2 new LeadSurfaced events", surfaced.size in 1..2)
    }

    @Test
    fun `LeadSurfaced not emitted on non-surface ticks`() {
        // currentDay=11 → 11 % 3 != 0 → no leads surface
        val w = world(currentDay = 11)
        assertTrue("Expected no LeadSurfaced on non-surface tick",
            generateEvents(w).none { it is SimEvent.LeadSurfaced }
        )
    }

    // --- PursueLead removes from surfacedLeads and advances negotiation ---

    @Test
    fun `PursueLead removes prospect from surfacedLeads`() {
        val w = world(surfacedLeads = setOf("p0"))
        val option = ResponseOption("id", "Pursue", listOf(StateEffect.PursueLead("p0")))
        val (newWorld, _) = applyResponse(w, option)
        assertTrue("p0 should be removed from surfacedLeads after pursue", "p0" !in newWorld.surfacedLeads)
    }

    @Test
    fun `PursueLead injects NegotiationRound event`() {
        val w = world(surfacedLeads = setOf("p0"))
        val option = ResponseOption("id", "Pursue", listOf(StateEffect.PursueLead("p0")))
        val (_, events) = applyResponse(w, option)
        assertNotNull("Expected NegotiationRound injected by PursueLead",
            events.filterIsInstance<SimEvent.NegotiationRound>().firstOrNull { it.prospectId == "p0" }
        )
    }
}
