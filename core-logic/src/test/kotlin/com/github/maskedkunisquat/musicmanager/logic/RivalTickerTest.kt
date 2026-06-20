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
import com.github.maskedkunisquat.musicmanager.logic.sim.RivalTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ===== Prospect pursuit =====

    @Test
    fun `rival does nothing when prospect pool is empty`() {
        val ticker = RivalTicker()
        val world = baseWorld()
        val (_, events) = ticker.tick(world, rng)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `rival accumulates counter without signing before threshold`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val allEvents = mutableListOf<SimEvent>()
        repeat(RIVAL_PROSPECT_THRESHOLD - 1) {
            val (_, events) = ticker.tick(world, rng)
            allEvents += events
        }
        assertTrue(allEvents.none { it is SimEvent.RivalSigning })
    }

    @Test
    fun `rival signs prospect exactly at threshold`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        var current = world
        var signing: SimEvent.RivalSigning? = null
        repeat(RIVAL_PROSPECT_THRESHOLD) {
            val (w, events) = ticker.tick(current, rng)
            current = w
            signing = events.filterIsInstance<SimEvent.RivalSigning>().firstOrNull() ?: signing
        }
        assertNotNull(signing)
        assertEquals("Prospect p0", signing!!.prospectName)
        assertEquals("r0", signing!!.rivalId)
    }

    @Test
    fun `signed prospect is removed from world`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        var current = world
        repeat(RIVAL_PROSPECT_THRESHOLD) {
            val (w, _) = ticker.tick(current, rng)
            current = w
        }
        assertFalse(current.prospects.containsKey("p0"))
    }

    @Test
    fun `wasPlayerTarget is true when prospect was in activeNegotiations`() {
        val ticker = RivalTicker()
        val world = baseWorld(
            prospects = mapOf("p0" to prospect("p0")),
            activeNegotiations = mapOf("p0" to 1)
        )
        var current = world
        var signing: SimEvent.RivalSigning? = null
        repeat(RIVAL_PROSPECT_THRESHOLD) {
            val (w, events) = ticker.tick(current, rng)
            current = w
            signing = events.filterIsInstance<SimEvent.RivalSigning>().firstOrNull() ?: signing
        }
        assertTrue(signing!!.wasPlayerTarget)
    }

    @Test
    fun `wasPlayerTarget is false when prospect was not in activeNegotiations`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        var current = world
        var signing: SimEvent.RivalSigning? = null
        repeat(RIVAL_PROSPECT_THRESHOLD) {
            val (w, events) = ticker.tick(current, rng)
            current = w
            signing = events.filterIsInstance<SimEvent.RivalSigning>().firstOrNull() ?: signing
        }
        assertFalse(signing!!.wasPlayerTarget)
    }

    @Test
    fun `rival prefers high-weight genre prospects`() {
        val ticker = RivalTicker()
        // indie-rock weight=1.0, pop weight=0.3 in rival; both with equal signability
        val world = baseWorld(prospects = mapOf(
            "p_pop" to prospect("p_pop", genre = "pop", signability = 0.9f),
            "p_indie" to prospect("p_indie", genre = "indie-rock", signability = 0.8f)
        ))
        // Run deterministically with fixed seed; indie-rock should win despite lower signability
        // (0.8 * 1.0 = 0.80 vs 0.9 * 0.3 = 0.27)
        var current = world
        var signing: SimEvent.RivalSigning? = null
        val deterministicRng = Random(1L)
        repeat(RIVAL_PROSPECT_THRESHOLD) {
            val (w, events) = ticker.tick(current, deterministicRng)
            current = w
            signing = events.filterIsInstance<SimEvent.RivalSigning>().firstOrNull() ?: signing
        }
        assertEquals("indie-rock", signing!!.genre)
    }

    @Test
    fun `rival picks new target after signing`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf(
            "p0" to prospect("p0"),
            "p1" to prospect("p1")
        ))
        var current = world
        // First signing
        repeat(RIVAL_PROSPECT_THRESHOLD) { val (w, _) = ticker.tick(current, rng); current = w }
        // Second target — p0 gone, should move to p1
        repeat(RIVAL_PROSPECT_THRESHOLD) { val (w, _) = ticker.tick(current, rng); current = w }
        assertFalse(current.prospects.containsKey("p1"))
    }

    @Test
    fun `rival signs apply PRESS rep penalty`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0")))
        val initialPress = world.label.reputation[ReputationCommunity.PRESS]!!
        var current = world
        repeat(RIVAL_PROSPECT_THRESHOLD) { val (w, _) = ticker.tick(current, rng); current = w }
        assertTrue(current.label.reputation[ReputationCommunity.PRESS]!! < initialPress)
    }

    @Test
    fun `rival does not target unavailable prospects`() {
        val ticker = RivalTicker()
        val world = baseWorld(
            prospects = mapOf("p0" to prospect("p0")),
            // Mark p0 as unavailable — rival should do nothing
        ).copy(unavailableProspects = setOf("p0"))
        val (_, events) = ticker.tick(world, rng)
        assertTrue(events.none { it is SimEvent.RivalSigning })
    }

    // ===== Poach pursuit =====

    @Test
    fun `rival does not poach when no artist meets conditions`() {
        val ticker = RivalTicker()
        // Artist with high loyalty — not poachable
        val a = artist("a0", loyalty = 0.8f)
        val c = contract("ca0", "a0", expiryDay = 5)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        val (_, events) = ticker.tick(world, rng)
        assertTrue(events.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `rival does not poach artist whose contract has more than 15 ticks remaining`() {
        val ticker = RivalTicker()
        val a = artist("a0", loyalty = 0.1f)
        // expiryDay=20, currentDay=0 → 20 ticks remaining > 15
        val c = contract("ca0", "a0", expiryDay = 20)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        repeat(RIVAL_POACH_THRESHOLD) { ticker.tick(world, rng) }
        val (_, events) = ticker.tick(world, rng)
        assertTrue(events.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `rival poaches artist exactly at threshold`() {
        val ticker = RivalTicker()
        val a = artist("a0", loyalty = 0.1f)
        // expiryDay=10, currentDay=0 → 10 ticks remaining ≤ 15
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        var current = world
        var poach: SimEvent.RivalPoach? = null
        repeat(RIVAL_POACH_THRESHOLD) {
            val (w, events) = ticker.tick(current, rng)
            current = w
            poach = events.filterIsInstance<SimEvent.RivalPoach>().firstOrNull() ?: poach
        }
        assertNotNull(poach)
        assertEquals("a0", poach!!.artistId)
        assertEquals("Artist a0", poach!!.artistName)
    }

    @Test
    fun `poached artist is removed from world`() {
        val ticker = RivalTicker()
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        var current = world
        repeat(RIVAL_POACH_THRESHOLD) { val (w, _) = ticker.tick(current, rng); current = w }
        assertFalse(current.artists.containsKey("a0"))
        assertFalse(current.label.rosterIds.contains("a0"))
        assertFalse(current.contracts.containsKey("ca0"))
    }

    @Test
    fun `poach applies PRESS rep penalty`() {
        val ticker = RivalTicker()
        val a = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val world = baseWorld(artists = mapOf("a0" to a), contracts = mapOf("ca0" to c), currentDay = 0)
        val initialPress = world.label.reputation[ReputationCommunity.PRESS]!!
        var current = world
        repeat(RIVAL_POACH_THRESHOLD) { val (w, _) = ticker.tick(current, rng); current = w }
        assertTrue(current.label.reputation[ReputationCommunity.PRESS]!! < initialPress)
    }

    @Test
    fun `rival drops poach target if loyalty recovers above threshold`() {
        val ticker = RivalTicker()
        // Start with low loyalty and near-expiry contract to activate poach target selection
        val initialArtist = artist("a0", loyalty = 0.1f)
        val c = contract("ca0", "a0", expiryDay = 10)
        val worldLow = baseWorld(artists = mapOf("a0" to initialArtist), contracts = mapOf("ca0" to c))
        // Get rival to pick a0 as poach target
        ticker.tick(worldLow, rng)
        // Now present a world where loyalty is high — rival should abandon pursuit
        val highLoyaltyArtist = initialArtist.copy(dimensions = initialArtist.dimensions.copy(loyalty = 0.9f))
        val worldHigh = baseWorld(artists = mapOf("a0" to highLoyaltyArtist), contracts = mapOf("ca0" to c))
        repeat(RIVAL_POACH_THRESHOLD) { ticker.tick(worldHigh, rng) }
        val (_, events) = ticker.tick(worldHigh, rng)
        assertTrue(events.none { it is SimEvent.RivalPoach })
    }

    @Test
    fun `no rival action when rivals map is empty`() {
        val ticker = RivalTicker()
        val world = baseWorld(prospects = mapOf("p0" to prospect("p0"))).copy(rivals = emptyMap())
        val (_, events) = ticker.tick(world, rng)
        assertTrue(events.isEmpty())
    }
}
