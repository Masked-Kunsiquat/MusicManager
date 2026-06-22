package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.NewSeasonInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSeasonInitializerTest {

    private val artistId = "artist_42_0"
    private val contractId = "contract_42_0"

    private fun baseWorld(
        funds: Long = 100_000_00L,
        artistBalance: Float = 0.6f,
        contractExpiryDay: Int = 220  // 40 ticks past season end
    ): SimWorld {
        val artist = ArtistState(
            id = artistId,
            name = "Test Artist",
            genre = "indie-rock",
            dimensions = ArtistDimensions(
                confidence = 0.7f,
                commercialAppetite = 0.4f,
                volatility = 0.3f,
                loyalty = 0.5f
            ),
            needs = mapOf(NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.7f, 0.02f)),
            activeWants = emptyList(),
            contractId = contractId,
            relationshipBalance = artistBalance
        )
        val contract = Contract(
            id = contractId,
            artistId = artistId,
            startDay = 0,
            expiryDay = contractExpiryDay,
            revenueSplit = RevenueSplit(50),
            creativeControl = CreativeControl.SHARED
        )
        return SimWorld(
            seed = 42L,
            currentDay = 180,
            artists = mapOf(artistId to artist),
            label = LabelState(
                funds = funds,
                reputation = ReputationCommunity.entries.associateWith { 0.5f },
                rosterIds = setOf(artistId),
                capabilities = setOf(CapabilityType.PUBLICIST),
                tasteVector = mapOf("indie-rock" to 0.7f)
            ),
            market = MarketState(genreTrends = mapOf("indie-rock" to 0.8f, "pop" to 0.3f)),
            contracts = mapOf(contractId to contract),
            season = SeasonState(seasonNumber = 1, seasonStartTick = 0, seasonEndTick = 180)
        )
    }

    // --- currentDay ---

    @Test
    fun `currentDay resets to 0`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertEquals(0, next.currentDay)
    }

    // --- seasonNumber ---

    @Test
    fun `seasonNumber increments by 1`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertEquals(2, next.season.seasonNumber)
    }

    // --- roster carry-over ---

    @Test
    fun `all artists carry over to the new season`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertNotNull(next.artists[artistId])
    }

    @Test
    fun `relationshipBalance is halved on carry-over`() {
        val world = baseWorld(artistBalance = 0.6f)
        val next = NewSeasonInitializer.advance(world)
        assertEquals(0.3f, next.artists[artistId]!!.relationshipBalance, 0.001f)
    }

    @Test
    fun `wantLastSurfacedAt is cleared`() {
        val artist = baseWorld().artists[artistId]!!.copy(wantLastSurfacedAt = mapOf("RECORD_ALBUM" to 100))
        val world = baseWorld().copy(artists = mapOf(artistId to artist))
        val next = NewSeasonInitializer.advance(world)
        assertTrue(next.artists[artistId]!!.wantLastSurfacedAt.isEmpty())
    }

    @Test
    fun `lastInteractionDay resets to 0`() {
        val artist = baseWorld().artists[artistId]!!.copy(lastInteractionDay = 150)
        val world = baseWorld().copy(artists = mapOf(artistId to artist))
        val next = NewSeasonInitializer.advance(world)
        assertEquals(0, next.artists[artistId]!!.lastInteractionDay)
    }

    // --- contracts ---

    @Test
    fun `contract expiryDay is rebased to new season start`() {
        // contract expires at tick 220, season ends at 180 → 40 ticks remaining → expiryDay=40
        val next = NewSeasonInitializer.advance(baseWorld(contractExpiryDay = 220))
        assertEquals(40, next.contracts[contractId]!!.expiryDay)
    }

    @Test
    fun `lapsed contract gets grace period of 1 tick`() {
        // contract already expired (expiryDay = 160, before season end at 180)
        val next = NewSeasonInitializer.advance(baseWorld(contractExpiryDay = 160))
        assertEquals(1, next.contracts[contractId]!!.expiryDay)
    }

    @Test
    fun `rebased contract startDay is 0`() {
        val next = NewSeasonInitializer.advance(baseWorld(contractExpiryDay = 220))
        assertEquals(0, next.contracts[contractId]!!.startDay)
    }

    // --- funds ---

    @Test
    fun `funds carry over at 60 percent`() {
        val next = NewSeasonInitializer.advance(baseWorld(funds = 100_000_00L))
        assertEquals(60_000_00L, next.label.funds)
    }

    @Test
    fun `funds floor is applied when 60 percent would be below 10000 dollars`() {
        val next = NewSeasonInitializer.advance(baseWorld(funds = 5_000_00L))
        assertEquals(10_000_00L, next.label.funds)
    }

    // --- capabilities + tasteVector ---

    @Test
    fun `capabilities carry over unchanged`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertTrue(CapabilityType.PUBLICIST in next.label.capabilities)
    }

    @Test
    fun `tasteVector carries over unchanged`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertEquals(0.7f, next.label.tasteVector["indie-rock"]!!, 0.001f)
    }

    // --- reputation ---

    @Test
    fun `reputation carries over unchanged`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        for (community in ReputationCommunity.entries) {
            assertEquals(0.5f, next.label.reputation[community]!!, 0.001f)
        }
    }

    // --- market ---

    @Test
    fun `genreTrends drift 50 percent toward 0_5`() {
        // indie-rock starts at 0.8 → drifts to 0.8 + (0.5 - 0.8) * 0.5 = 0.65
        val next = NewSeasonInitializer.advance(baseWorld())
        assertEquals(0.65f, next.market.genreTrends["indie-rock"]!!, 0.001f)
    }

    @Test
    fun `genre trends are not fully reset to 0_5`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertNotEquals(0.5f, next.market.genreTrends["indie-rock"]!!)
    }

    // --- prospect pool ---

    @Test
    fun `new season generates a fresh prospect pool`() {
        val world = baseWorld()
        val next = NewSeasonInitializer.advance(world)
        // No overlap in prospect IDs (old prospects from seed=42, new from XOR seed)
        val oldIds = world.prospects.keys
        val newIds = next.prospects.keys
        assertTrue("Expected fresh prospect IDs", oldIds.intersect(newIds).isEmpty())
    }

    @Test
    fun `prospect pool size is 6-10 plus one whale`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertTrue(next.prospects.size in 7..11)
    }

    @Test
    fun `same base seed produces deterministic next season`() {
        val next1 = NewSeasonInitializer.advance(baseWorld())
        val next2 = NewSeasonInitializer.advance(baseWorld())
        assertEquals(next1.prospects.keys, next2.prospects.keys)
    }

    @Test
    fun `different base seeds produce different prospect pools`() {
        val world1 = baseWorld()
        val world2 = baseWorld().copy(seed = 99L)
        val next1 = NewSeasonInitializer.advance(world1)
        val next2 = NewSeasonInitializer.advance(world2)
        assertNotEquals(next1.prospects.keys, next2.prospects.keys)
    }

    // --- cleared state ---

    @Test
    fun `activeRenewals is cleared`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 2))
        val next = NewSeasonInitializer.advance(world)
        assertTrue(next.activeRenewals.isEmpty())
    }

    @Test
    fun `activeNegotiations is cleared`() {
        val world = baseWorld().copy(activeNegotiations = mapOf("prospect_0" to 3))
        val next = NewSeasonInitializer.advance(world)
        assertTrue(next.activeNegotiations.isEmpty())
    }

    @Test
    fun `surfacedLeads is cleared`() {
        val world = baseWorld().copy(surfacedLeads = setOf("prospect_0"))
        val next = NewSeasonInitializer.advance(world)
        assertTrue(next.surfacedLeads.isEmpty())
    }

    @Test
    fun `intelCache is cleared`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertTrue(next.label.intelCache.isEmpty())
    }

    @Test
    fun `seasonEndedEmitted resets to false`() {
        val world = baseWorld().copy(seasonEndedEmitted = true)
        val next = NewSeasonInitializer.advance(world)
        assertFalse(next.seasonEndedEmitted)
    }

    // --- deadlines ---

    @Test
    fun `new deadlines are seeded for the new season`() {
        val next = NewSeasonInitializer.advance(baseWorld())
        assertTrue(next.deadlines.isNotEmpty())
        assertTrue(next.deadlines.values.all { it.id.endsWith(":2") })
    }

    // --- SeasonState ---

    @Test
    fun `new season startFunds matches carried funds`() {
        val next = NewSeasonInitializer.advance(baseWorld(funds = 100_000_00L))
        assertEquals(60_000_00L, next.season.startFunds)
    }
}
