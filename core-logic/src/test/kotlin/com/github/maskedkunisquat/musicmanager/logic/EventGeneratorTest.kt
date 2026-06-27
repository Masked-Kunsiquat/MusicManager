package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EventGeneratorTest {

    private val artist = ArtistState(
        id = "a0",
        name = "Test Artist",
        genre = "indie-rock",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        needs = NeedType.entries.associateWith { NeedState(it, 0.8f, 0.02f) },
        activeWants = emptyList(),
        contractId = null
    )

    private fun world(
        genreTrends: Map<String, Float> = mapOf("indie-rock" to 0.5f),
        artists: Map<String, ArtistState> = mapOf("a0" to artist)
    ) = SimWorld(
        seed = 1L,
        currentDay = 10,
        artists = artists,
        label = LabelState(
            funds = 100_000_00L,
            reputation = ReputationCommunity.entries.associateWith { 0.5f },
            rosterIds = artists.keys.toSet()
        ),
        market = MarketState(genreTrends),
        contracts = emptyMap()
    )

    // --- MarketShift ---

    @Test
    fun `MarketShift emitted when genre moves by at least THRESHOLD`() {
        val previous = MarketState(mapOf("indie-rock" to 0.40f))
        val current = world(genreTrends = mapOf("indie-rock" to 0.50f))  // delta = 0.10f >= 0.08f
        val events = generateEvents(current, previous)
        assertTrue(events.any { it is SimEvent.MarketShift && it.genre == "indie-rock" })
    }

    @Test
    fun `MarketShift not emitted when movement is below THRESHOLD`() {
        val previous = MarketState(mapOf("indie-rock" to 0.48f))
        val current = world(genreTrends = mapOf("indie-rock" to 0.50f))  // delta = 0.02f < 0.03f
        val events = generateEvents(current, previous)
        assertTrue(events.none { it is SimEvent.MarketShift })
    }

    @Test
    fun `MarketShift carries correct previous and current trend values`() {
        val previous = MarketState(mapOf("hip-hop" to 0.30f))
        val current = world(genreTrends = mapOf("hip-hop" to 0.42f))
        val event = generateEvents(current, previous).filterIsInstance<SimEvent.MarketShift>().first()
        assertEquals(0.30f, event.previousTrend, 0.001f)
        assertEquals(0.42f, event.currentTrend, 0.001f)
    }

    @Test
    fun `MarketShift emitted for each genre that crosses threshold`() {
        val previous = MarketState(mapOf("pop" to 0.30f, "folk" to 0.30f, "hip-hop" to 0.49f))
        val current = world(genreTrends = mapOf("pop" to 0.42f, "folk" to 0.42f, "hip-hop" to 0.50f))
        val shifts = generateEvents(current, previous).filterIsInstance<SimEvent.MarketShift>()
        assertEquals(2, shifts.size)  // pop and folk cross; hip-hop (0.01f delta) does not
        assertTrue(shifts.any { it.genre == "pop" })
        assertTrue(shifts.any { it.genre == "folk" })
    }

    @Test
    fun `no MarketShift when previousMarket equals current market`() {
        val market = MarketState(mapOf("indie-rock" to 0.5f))
        val current = world(genreTrends = mapOf("indie-rock" to 0.5f))
        val events = generateEvents(current, market)
        assertTrue(events.none { it is SimEvent.MarketShift })
    }

    // --- IntelDrop ---

    @Test
    fun `IntelDrop emitted on favourable RNG roll`() {
        // Seed chosen to produce nextFloat() < 0.25 on first call.
        val world = world(genreTrends = mapOf("indie-rock" to 0.6f))
        // Try 100 seeds — at least some should emit an IntelDrop.
        val emitted = (0..99).any { seed ->
            generateEvents(world, rng = Random(seed)).any { it is SimEvent.IntelDrop }
        }
        assertTrue("Expected at least one IntelDrop across 100 seeds", emitted)
    }

    @Test
    fun `IntelDrop not emitted when RNG roll fails`() {
        // Seed 0 with the default fixed seed emits no IntelDrop (tested empirically).
        val world = world(genreTrends = mapOf("indie-rock" to 0.6f))
        // With the default rng (Random(0L)), check outcome is deterministic.
        val eventsA = generateEvents(world)
        val eventsB = generateEvents(world)
        assertEquals(eventsA, eventsB)
        assertTrue("Expected no IntelDrop with default seed", eventsA.none { it is SimEvent.IntelDrop })
    }

    @Test
    fun `IntelDrop genre is one of the market genres`() {
        val genreTrends = mapOf("indie-rock" to 0.6f, "pop" to 0.4f, "folk" to 0.7f)
        val world = world(genreTrends = genreTrends)
        val drops = (0..200).flatMap { seed ->
            generateEvents(world, rng = Random(seed)).filterIsInstance<SimEvent.IntelDrop>()
        }
        assertTrue("Expected some IntelDrops across seeds", drops.isNotEmpty())
        drops.forEach { drop ->
            assertTrue("IntelDrop genre ${drop.genre} not in market", drop.genre in genreTrends)
        }
    }

    @Test
    fun `IntelDrop prefers roster genre over non-roster genre`() {
        // Artist is indie-rock; market also has pop and folk.
        val genreTrends = mapOf("indie-rock" to 0.5f, "pop" to 0.5f, "folk" to 0.5f)
        val world = world(genreTrends = genreTrends)
        val drops = (0..500).flatMap { seed ->
            generateEvents(world, rng = Random(seed)).filterIsInstance<SimEvent.IntelDrop>()
        }
        val rosterDrops = drops.count { it.genre == "indie-rock" }
        val total = drops.size
        assertTrue("Expected roster genre to dominate IntelDrops; got $rosterDrops/$total", rosterDrops > total / 2)
    }

    // --- LabelNeedUrgent ---

    @Test
    fun `LabelNeedUrgent CASH_FLOW emitted when funds below threshold`() {
        // $4k in cents — below $5k bucket → severity 0.10f, threshold 0.35f → should emit
        val w = world().copy(label = world().label.copy(funds = 400_000L))
        val events = generateEvents(w)
        val urgent = events.filterIsInstance<SimEvent.LabelNeedUrgent>()
            .firstOrNull { it.needType == LabelNeedType.CASH_FLOW }
        assertNotNull("Expected CASH_FLOW LabelNeedUrgent", urgent)
        assertEquals(0.10f, urgent!!.severity, 0.001f)
    }

    @Test
    fun `LabelNeedUrgent CASH_FLOW not emitted when funds are healthy`() {
        // $100k in cents — above all thresholds → severity 1.0f, above 0.35f → should not emit
        val w = world().copy(label = world().label.copy(funds = 10_000_000L))
        val events = generateEvents(w)
        assertTrue(events.none { it is SimEvent.LabelNeedUrgent && (it as SimEvent.LabelNeedUrgent).needType == LabelNeedType.CASH_FLOW })
    }

    @Test
    fun `LabelNeedUrgent GENRE_DIVERSITY emitted when roster is one-genre`() {
        // All artists in same genre → 1 distinct / 4 = 0.25f < 0.40f threshold → should emit
        val sameGenreArtist = artist.copy(id = "a1", genre = "indie-rock")
        val artists = mapOf("a0" to artist, "a1" to sameGenreArtist)
        val w = world(artists = artists)
        val events = generateEvents(w)
        assertTrue(events.any { it is SimEvent.LabelNeedUrgent && (it as SimEvent.LabelNeedUrgent).needType == LabelNeedType.GENRE_DIVERSITY })
    }

    @Test
    fun `LabelNeedUrgent GENRE_DIVERSITY not emitted when roster is diverse`() {
        // Four artists across four distinct genres → 4/4 = 1.0f → no event
        val artists = mapOf(
            "a0" to artist.copy(genre = "indie-rock"),
            "a1" to artist.copy(id = "a1", genre = "hip-hop"),
            "a2" to artist.copy(id = "a2", genre = "folk"),
            "a3" to artist.copy(id = "a3", genre = "pop")
        )
        val w = world(genreTrends = mapOf("indie-rock" to 0.5f, "hip-hop" to 0.5f, "folk" to 0.5f, "pop" to 0.5f), artists = artists)
        val events = generateEvents(w)
        assertTrue(events.none { it is SimEvent.LabelNeedUrgent && (it as SimEvent.LabelNeedUrgent).needType == LabelNeedType.GENRE_DIVERSITY })
    }

    @Test
    fun `LabelNeedUrgent carries correct day`() {
        val w = world().copy(currentDay = 42, label = world().label.copy(funds = 400_000L))
        val event = generateEvents(w).filterIsInstance<SimEvent.LabelNeedUrgent>().first()
        assertEquals(42, event.dayOfGame)
    }

    @Test
    fun `LabelNeedUrgent CASH_FLOW suppressed within 10-tick cooldown`() {
        // currentDay=10, lastNoticed=5 → gap=5 < 10 → no event
        val w = world().copy(
            label = world().label.copy(funds = 400_000L),
            labelNeedNoticedAt = mapOf("CASH_FLOW" to 5)
        )
        assertTrue("Expected no CASH_FLOW event within cooldown",
            generateEvents(w).none { it is SimEvent.LabelNeedUrgent && (it as SimEvent.LabelNeedUrgent).needType == LabelNeedType.CASH_FLOW }
        )
    }

    @Test
    fun `LabelNeedUrgent CASH_FLOW emitted once cooldown expires`() {
        // currentDay=10, lastNoticed=0 → gap=10 which is NOT < 10 → event fires
        val w = world().copy(
            label = world().label.copy(funds = 400_000L),
            labelNeedNoticedAt = mapOf("CASH_FLOW" to 0)
        )
        assertNotNull("Expected CASH_FLOW event after cooldown expires",
            generateEvents(w).filterIsInstance<SimEvent.LabelNeedUrgent>()
                .firstOrNull { it.needType == LabelNeedType.CASH_FLOW }
        )
    }

    @Test
    fun `LabelNeedUrgent GENRE_DIVERSITY not emitted for single-artist roster`() {
        // 1 artist with 1 genre: diversity = 1/max(4,1) would be 0.25 without the size<2 guard
        val w = world()  // default world has exactly 1 artist
        assertTrue(generateEvents(w).none { it is SimEvent.LabelNeedUrgent && (it as SimEvent.LabelNeedUrgent).needType == LabelNeedType.GENRE_DIVERSITY })
    }

    // --- Determinism ---

    @Test
    fun `generateEvents is deterministic for identical inputs`() {
        val world = world(genreTrends = mapOf("pop" to 0.55f, "folk" to 0.45f))
        val previous = MarketState(mapOf("pop" to 0.45f, "folk" to 0.55f))
        val rng = Random(42)
        val a = generateEvents(world, previous, Random(42))
        val b = generateEvents(world, previous, Random(42))
        assertEquals(a, b)
    }
}
