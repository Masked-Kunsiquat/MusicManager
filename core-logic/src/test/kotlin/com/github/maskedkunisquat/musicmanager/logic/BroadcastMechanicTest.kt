package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.Want
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BroadcastMechanicTest {

    private val ai = StubAiProvider()

    private fun artist(
        id: String = "a0",
        genre: String = "indie-rock",
        needs: Map<NeedType, Float> = NeedType.entries.associateWith { 0.8f },
        activeWants: List<Want> = emptyList(),
        wantLastSurfacedAt: Map<String, Int> = emptyMap()
    ) = ArtistState(
        id = id,
        name = "Artist $id",
        genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        needs = needs.entries.associate { (t, v) -> t to NeedState(t, v, 0.02f) },
        activeWants = activeWants,
        contractId = null,
        wantLastSurfacedAt = wantLastSurfacedAt
    )

    private fun world(
        vararg artists: ArtistState,
        genreTrends: Map<String, Float> = artists.associate { it.genre to 0.5f },
        currentDay: Int = 10
    ): SimWorld {
        val artistMap = artists.associate { it.id to it }
        return SimWorld(
            seed = 1L,
            currentDay = currentDay,
            artists = artistMap,
            label = LabelState(funds = 10_000_000L, reputation = emptyMap(), rosterIds = artistMap.keys),
            market = MarketState(genreTrends),
            contracts = emptyMap()
        )
    }

    // --- Option priority: WantSatisfied floats first ---

    @Test
    fun `option carrying WantSatisfied for active want is listed first`() = runBlocking {
        val want = Want(WantType.MAJOR_VENUE_TOUR, urgency = 0.9f, expiryDay = null)
        val a = artist(activeWants = listOf(want))
        val w = world(a)
        val event = SimEvent.WantSurfaced(artistId = a.id, wantType = WantType.MAJOR_VENUE_TOUR, urgency = 0.9f, dayOfGame = 10)
        val options = ai.generateEmail(event, w).options
        assertTrue("Expected first option to carry WantSatisfied for MAJOR_VENUE_TOUR",
            options.first().effects.any {
                it is StateEffect.WantSatisfied && it.wantType == WantType.MAJOR_VENUE_TOUR
            }
        )
    }

    // --- Option priority: cross-need side effect reorders when that need is critical ---

    @Test
    fun `option with critical side-need effect reordered above option without it`() = runBlocking {
        // BELONGING is low (triggers NeedUrgent) and CREATIVE_FULFILLMENT is critically low.
        // belong_collab gives both BELONGING + CF effects; label_dinner gives only a RosterNeedChange(BELONGING).
        // With CF = 0.05f, belong_collab should score higher and be placed first.
        val needs = NeedType.entries.associateWith { t ->
            when (t) {
                NeedType.BELONGING -> 0.15f
                NeedType.CREATIVE_FULFILLMENT -> 0.05f
                else -> 0.8f
            }
        }
        val a = artist(needs = needs)
        val w = world(a)
        val event = SimEvent.NeedUrgent(artistId = a.id, needType = NeedType.BELONGING, currentValue = 0.15f, dayOfGame = 10)
        val options = ai.generateEmail(event, w).options
        assertTrue("Expected first option to address CREATIVE_FULFILLMENT when it is critically low",
            options.first().effects.any {
                it is StateEffect.NeedChange && it.needType == NeedType.CREATIVE_FULFILLMENT && it.delta > 0f
            }
        )
    }

    // --- Option priority: conflicting option (negative delta on low need) sinks ---

    @Test
    fun `option with negative delta on critically low need scores below neutral options`() = runBlocking {
        // AUTONOMY is critically low. The GENRE_EXPERIMENT "stay on brand" option applies
        // NeedChange(AUTONOMY, -0.15f) — it should be last.
        val needs = NeedType.entries.associateWith { t ->
            if (t == NeedType.AUTONOMY) 0.10f else 0.8f
        }
        val want = Want(WantType.GENRE_EXPERIMENT, urgency = 0.9f, expiryDay = null)
        val a = artist(needs = needs, activeWants = listOf(want))
        val w = world(a)
        val event = SimEvent.WantSurfaced(artistId = a.id, wantType = WantType.GENRE_EXPERIMENT, urgency = 0.9f, dayOfGame = 10)
        val options = ai.generateEmail(event, w).options
        val last = options.last()
        assertTrue("Expected option with AUTONOMY penalty to be last when AUTONOMY is critically low",
            last.effects.any {
                it is StateEffect.NeedChange && it.needType == NeedType.AUTONOMY && it.delta < 0f
            }
        )
    }

    // --- IntelDrop: recognition-need genre weighting ---

    @Test
    fun `IntelDrop picks genre of recognition-hungry artist more than genre of healthy artist`() {
        // a0 (indie-rock) has critically low RECOGNITION — their genre gets double weight in the pool.
        // a1 (pop) has healthy needs — pop gets single weight.
        val aLowRec = artist(
            id = "a0", genre = "indie-rock",
            needs = NeedType.entries.associateWith { t ->
                if (t == NeedType.RECOGNITION) 0.10f else 0.8f
            }
        )
        val aHealthy = artist(id = "a1", genre = "pop")
        val w = world(
            aLowRec, aHealthy,
            genreTrends = mapOf("indie-rock" to 0.5f, "pop" to 0.5f)
        )
        val drops = (0..500).flatMap { seed ->
            generateEvents(w, rng = Random(seed)).filterIsInstance<SimEvent.IntelDrop>()
        }
        assertTrue("Expected IntelDrops across seeds", drops.isNotEmpty())
        val indieCount = drops.count { it.genre == "indie-rock" }
        val popCount = drops.count { it.genre == "pop" }
        assertTrue(
            "Expected recognition-hungry genre (indie-rock: $indieCount) picked more than healthy genre (pop: $popCount)",
            indieCount > popCount
        )
    }

    // --- Want resurface cadence ---

    @Test
    fun `WantSurfaced suppressed when last surfaced within 5-tick cadence window`() {
        val want = Want(WantType.RECORD_ALBUM, urgency = 0.9f, expiryDay = null)
        // currentDay=10, lastSurfaced=7 → gap=3 < 5 → suppressed
        val a = artist(activeWants = listOf(want), wantLastSurfacedAt = mapOf("RECORD_ALBUM" to 7))
        val w = world(a)
        assertTrue("Expected WantSurfaced suppressed within cadence window",
            generateEvents(w).none {
                it is SimEvent.WantSurfaced && it.wantType == WantType.RECORD_ALBUM
            }
        )
    }

    @Test
    fun `WantSurfaced emitted once cadence window has expired`() {
        val want = Want(WantType.RECORD_ALBUM, urgency = 0.9f, expiryDay = null)
        // currentDay=10, lastSurfaced=-2 → gap=12 which is NOT < 12 → emitted
        val a = artist(activeWants = listOf(want), wantLastSurfacedAt = mapOf("RECORD_ALBUM" to -2))
        val w = world(a)
        assertNotNull("Expected WantSurfaced emitted after cadence window",
            generateEvents(w).filterIsInstance<SimEvent.WantSurfaced>()
                .firstOrNull { it.wantType == WantType.RECORD_ALBUM }
        )
    }

    @Test
    fun `SimEngine stamps wantLastSurfacedAt after WantSurfaced emitted`() {
        val want = Want(WantType.RECORD_ALBUM, urgency = 0.9f, expiryDay = null)
        val a = artist(activeWants = listOf(want))
        val w = world(a)
        // Confirm the want surfaces on this world (no prior stamp, urgency >= 0.7f).
        val events = generateEvents(w)
        val surfaced = events.filterIsInstance<SimEvent.WantSurfaced>().firstOrNull { it.wantType == WantType.RECORD_ALBUM }
        assertNotNull("Precondition: WantSurfaced should emit with no prior stamp", surfaced)
        // SimEngine stamps wantLastSurfacedAt; verify via tick.
        val engine = com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine()
        val result = engine.tick(w)
        val stamp = result.world.artists[a.id]?.wantLastSurfacedAt?.get("RECORD_ALBUM")
        assertNotNull("Expected wantLastSurfacedAt stamped on artist after WantSurfaced emitted", stamp)
    }
}
