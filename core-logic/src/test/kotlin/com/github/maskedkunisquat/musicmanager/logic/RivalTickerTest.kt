package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.RivalState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.RIVAL_POACH_THRESHOLD
import com.github.maskedkunisquat.musicmanager.logic.sim.RIVAL_PROSPECT_THRESHOLD
import com.github.maskedkunisquat.musicmanager.logic.sim.tickRivals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RivalTickerTest {

    private val rng = Random(42)

    private val rival = RivalState(
        id = "r0",
        name = "Mercury Sound",
        genreWeights = mapOf("indie-rock" to 1.0f, "pop" to 0.3f)
    )

    private fun prospect(id: String, genre: String = "indie-rock", signability: Float = 0.8f) = ProspectState(
        id = id, name = "Prospect $id", genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        signabilityScore = signability
    )

    private fun artist(id: String, genre: String = "indie-rock", loyalty: Float = 0.1f, contractId: String = "c$id") = ArtistState(
        id = id, name = "Artist $id", genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, loyalty),
        needs = NeedType.entries.associateWith { NeedState(it, 0.8f, 0.02f) },
        activeWants = emptyList(),
        contractId = contractId
    )

    private fun contract(id: String, artistId: String, expiryDay: Int) = Contract(
        id = id, artistId = artistId,
        startDay = 0, expiryDay = expiryDay,
        revenueSplit = RevenueSplit(50), creativeControl = CreativeControl.SHARED
    )

    private fun baseWorld(
        prospects: Map<String, ProspectState> = emptyMap(),
        artists: Map<String, ArtistState> = emptyMap(),
        contracts: Map<String, Contract> = emptyMap(),
        activeNegotiations: Map<String, Int> = emptyMap(),
        currentDay: Int = 0
    ) = SimWorld(
        seed = 1L, currentDay = currentDay,
        artists = artists,
        label = LabelState(
            funds = 1_000_000L,
            reputation = ReputationCommunity.entries.associateWith { 0.5f },
            rosterIds = artists.keys.toSet()
        ),
        market = MarketState(emptyMap()),
        contracts = contracts,
        prospects = prospects,
        rivals = mapOf("r0" to rival),
        activeNegotiations = activeNegotiations
    )

    // Runs N ticks, threading the world through each one. Returns final world + all events.
    private fun runTicks(initial: SimWorld, n: Int, r: Random = rng): Pair<SimWorld, List<SimEvent>> {
        var current = initial
        val allEvents = mutableListOf<SimEvent>()
        repeat(n) {
            val (w, events) = tickRivals(current, r)
            current = w; allEvents += events
        }
        return current to allEvents
    }

    // ===== Prospect pursuit =====

    @Test
    fun `rival does nothing when prospect pool is empty`() {
        val (_, events) = tickRivals(baseWorld(), rng)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `rival accumulates counter without signing before threshold`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val (_, allEvents) = runTicks(world, RIVAL_PROSPECT_THRESHOLD - 1)
        assertTrue(allEvents.none { it is SimEvent.RivalSigning })
    }

    @Test
    fun `rival signs prospect exactly at threshold`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val (_, allEvents) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        val signing = allEvents.filterIsInstance<SimEvent.RivalSigning>().firstOrNull()
        assertNotNull(signing)
        assertEquals("Prospect p0", signing!!.prospectName)
        assertEquals("r0", signing.rivalId)
    }

    @Test
    fun `signed prospect is removed from world`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val (finalWorld, _) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        assertFalse(finalWorld.prospects.containsKey("p0"))
    }

    @Test
    fun `rival counter persists across ticks via world state`() {
        // Verify that counter is stored on world, not in a separate object.
        // Manually inspect the world after each tick to see counter increment.
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val (w1, _) = tickRivals(world, rng)
        assertEquals(1, w1.rivalProspectCounters["r0"])
        val (w2, _) = tickRivals(w1, rng)
        assertEquals(2, w2.rivalProspectCounters["r0"])
    }

    @Test
    fun `wasPlayerTarget is true when prospect was in activeNegotiations`() {
        val world = baseWorld(
            prospects = mapOf("p0" to prospect("p0")),
            activeNegotiations = mapOf("p0" to 1)
        )
        val (_, allEvents) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        val signing = allEvents.filterIsInstance<SimEvent.RivalSigning>().firstOrNull()
        assertTrue(signing!!.wasPlayerTarget)
    }

    @Test
    fun `wasPlayerTarget is false when prospect was not in activeNegotiations`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val (_, allEvents) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        val signing = allEvents.filterIsInstance<SimEvent.RivalSigning>().firstOrNull()
        assertFalse(signing!!.wasPlayerTarget)
    }

    @Test
    fun `rival prefers high-weight genre prospects`() {
        // indie-rock weight=1.0, pop weight=0.3; indie should win despite lower signability
        // (0.8 * 1.0 = 0.80 vs 0.9 * 0.3 = 0.27)
        val world = baseWorld(prospects = mapOf(
            "p_pop"   to prospect("p_pop",   genre = "pop",        signability = 0.9f),
            "p_indie" to prospect("p_indie", genre = "indie-rock", signability = 0.8f)
        ))
        val (_, allEvents) = runTicks(world, RIVAL_PROSPECT_THRESHOLD, Random(1L))
        val signing = allEvents.filterIsInstance<SimEvent.RivalSigning>().firstOrNull()
        assertEquals("indie-rock", signing!!.genre)
    }

    @Test
    fun `rival picks new target after signing`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0"), "p1" to prospect("p1")))
        val (afterFirst, _) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        val (afterSecond, _) = runTicks(afterFirst, RIVAL_PROSPECT_THRESHOLD)
        assertFalse(afterSecond.prospects.containsKey("p1"))
    }

    @Test
    fun `rival signing applies PRESS rep penalty`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val initialPress = world.label.reputation[ReputationCommunity.PRESS]!!
        val (finalWorld, _) = runTicks(world, RIVAL_PROSPECT_THRESHOLD)
        assertTrue(finalWorld.label.reputation[ReputationCommunity.PRESS]!! < initialPress)
    }

    @Test
    fun `rival does not target unavailable prospects`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
            .copy(unavailableProspects = setOf("p0"))
        val (_, events) = tickRivals(world, rng)
        assertTrue(events.none { it is SimEvent.RivalSigning })
    }

    // ===== Poach pursuit =====

    @Test
    fun `rival does not poach when no artist meets conditions`() {
        val a = artist("a0", loyalty = 0.8f)
        val c = contract("ca0", "a0", expiryDay = 5)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c))
        val (_, events) = tickRivals(world, rng)
        assertTrue(events.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `rival does not poach artist whose contract has more than 15 ticks remaining`() {
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 20)  // 20 ticks > 15
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        val (_, allEvents) = runTicks(world, RIVAL_POACH_THRESHOLD + 1)
        assertTrue(allEvents.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `rival poaches artist exactly at threshold`() {
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)  // 10 ticks ≤ 15
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        val (_, allEvents) = runTicks(world, RIVAL_POACH_THRESHOLD)
        val poach = allEvents.filterIsInstance<SimEvent.RivalPoach>().firstOrNull()
        assertNotNull(poach)
        assertEquals("a0", poach!!.artistId)
        assertEquals("Artist a0", poach.artistName)
    }

    @Test
    fun `poach counter persists across ticks via world state`() {
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c))
        val (w1, _) = tickRivals(world, rng)
        assertEquals(1, w1.rivalPoachCounters["r0"])
        val (w2, _) = tickRivals(w1, rng)
        assertEquals(2, w2.rivalPoachCounters["r0"])
    }

    @Test
    fun `poached artist is removed from world`() {
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c))
        val (finalWorld, _) = runTicks(world, RIVAL_POACH_THRESHOLD)
        assertFalse(finalWorld.artists.containsKey("a0"))
        assertFalse(finalWorld.label.rosterIds.contains("a0"))
        assertFalse(finalWorld.contracts.containsKey("ca0"))
    }

    @Test
    fun `poach applies PRESS rep penalty`() {
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c))
        val initialPress = world.label.reputation[ReputationCommunity.PRESS]!!
        val (finalWorld, _) = runTicks(world, RIVAL_POACH_THRESHOLD)
        assertTrue(finalWorld.label.reputation[ReputationCommunity.PRESS]!! < initialPress)
    }

    @Test
    fun `rival drops poach target if loyalty recovers above threshold`() {
        val initialArtist = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val worldLow = baseWorld(artists = mapOf("a0" to initialArtist), contracts = mapOf("ca0" to c))
        // Prime: one tick so rival selects a0 as target (counter = 1, stored on world)
        val (worldPrimed, _) = tickRivals(worldLow, rng)
        assertEquals(1, worldPrimed.rivalPoachCounters["r0"])
        // Loyalty recovers — splice into the primed world so rival state carries over
        val highLoyaltyArtist = initialArtist.copy(dimensions = initialArtist.dimensions.copy(loyalty = 0.9f))
        val worldHighLoyalty = worldPrimed.copy(artists = mapOf("a0" to highLoyaltyArtist))
        // Run many more ticks — rival should drop target and never reach threshold
        val (_, allEvents) = runTicks(worldHighLoyalty, RIVAL_POACH_THRESHOLD + 2)
        assertTrue(allEvents.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `no rival action when rivals map is empty`() {
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0"))).copy(rivals = emptyMap())
        val (_, events) = tickRivals(world, rng)
        assertTrue(events.isEmpty())
    }
}
