package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseApplicatorTest {

    private val artistId = "artist_0"

    private val baseWorld = SimWorld(
        seed = 1L,
        currentDay = 0,
        artists = mapOf(
            artistId to ArtistState(
                id = artistId,
                name = "Test Artist",
                genre = "indie-rock",
                dimensions = ArtistDimensions(
                    confidence = 0.5f,
                    commercialAppetite = 0.5f,
                    volatility = 0.5f,
                    loyalty = 0.5f
                ),
                needs = mapOf(
                    NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.6f, 0.03f),
                    NeedType.FINANCIAL_SECURITY to NeedState(NeedType.FINANCIAL_SECURITY, 0.4f, 0.02f)
                ),
                activeWants = emptyList(),
                contractId = null
            )
        ),
        label = LabelState(
            funds = 10_000_00L, // $10,000 in cents
            reputation = emptyMap(),
            rosterIds = setOf(artistId)
        ),
        market = MarketState(genreTrends = emptyMap()),
        contracts = emptyMap()
    )

    private fun option(
        id: String = "opt_1",
        cost: Long = 0L,
        effects: List<StateEffect> = emptyList()
    ) = ResponseOption(id = id, text = "Option", effects = effects, costFunds = cost)

    // --- costFunds ---

    @Test
    fun `costFunds is deducted from label funds`() {
        val (result, _) = applyResponse(baseWorld, option(cost = 500_00L))
        assertEquals(10_000_00L - 500_00L, result.label.funds)
    }

    @Test
    fun `insufficient funds throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            applyResponse(baseWorld, option(cost = 99_999_00L))
        }
    }

    @Test
    fun `zero cost option leaves funds unchanged`() {
        val (result, _) = applyResponse(baseWorld, option(cost = 0L))
        assertEquals(baseWorld.label.funds, result.label.funds)
    }

    // --- NeedChange ---

    @Test
    fun `NeedChange increases need value correctly`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.20f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(0.80f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange clamps at 1f on overflow`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.90f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(1.0f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange clamps at 0f on underflow`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, -1.0f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(0.0f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange for unknown artist is a no-op`() {
        val effect = StateEffect.NeedChange("unknown_artist", NeedType.CREATIVE_FULFILLMENT, +0.5f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld, result)
    }

    @Test
    fun `negative costFunds throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            applyResponse(baseWorld, option(cost = -500_00L))
        }
    }

    // --- LabelFundsChange ---

    @Test
    fun `LabelFundsChange adds income to label funds`() {
        val effect = StateEffect.LabelFundsChange(delta = 1_000_00L)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld.label.funds + 1_000_00L, result.label.funds)
    }

    @Test
    fun `LabelFundsChange with negative delta throws IllegalArgumentException`() {
        val effect = StateEffect.LabelFundsChange(delta = -500_00L)
        assertThrows(IllegalArgumentException::class.java) {
            applyResponse(baseWorld, option(effects = listOf(effect)))
        }
    }

    // --- RelationshipChange ---

    @Test
    fun `RelationshipChange updates loyalty correctly`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = +0.20f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(0.70f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RelationshipChange clamps loyalty at 1f`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = +0.80f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(1.0f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RelationshipChange clamps loyalty at 0f`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = -1.0f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(0.0f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    // --- RosterNeedChange ---

    private val secondArtistId = "artist_1"

    private val twoArtistWorld = baseWorld.copy(
        artists = baseWorld.artists + (secondArtistId to baseWorld.artists[artistId]!!.copy(
            id = secondArtistId,
            name = "Second Artist"
        )),
        label = baseWorld.label.copy(rosterIds = setOf(artistId, secondArtistId))
    )

    @Test
    fun `RosterNeedChange applies to all artists`() {
        val effect = StateEffect.RosterNeedChange(NeedType.CREATIVE_FULFILLMENT, +0.20f)
        val (result, _) = applyResponse(twoArtistWorld, option(effects = listOf(effect)))
        assertEquals(0.80f, result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
        assertEquals(0.80f, result.artists[secondArtistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
    }

    @Test
    fun `RosterNeedChange clamps each artist at 1f`() {
        val effect = StateEffect.RosterNeedChange(NeedType.CREATIVE_FULFILLMENT, +0.90f)
        val (result, _) = applyResponse(twoArtistWorld, option(effects = listOf(effect)))
        assertEquals(1.0f, result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
        assertEquals(1.0f, result.artists[secondArtistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
    }

    @Test
    fun `RosterNeedChange is no-op for artists missing that need type`() {
        val worldWithoutFinancialNeed = baseWorld.copy(
            artists = mapOf(artistId to baseWorld.artists[artistId]!!.copy(
                needs = mapOf(NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.6f, 0.03f))
            ))
        )
        val effect = StateEffect.RosterNeedChange(NeedType.FINANCIAL_SECURITY, +0.30f)
        val (result, _) = applyResponse(worldWithoutFinancialNeed, option(effects = listOf(effect)))
        // Artist doesn't have FINANCIAL_SECURITY need — world unchanged for that artist
        assertEquals(worldWithoutFinancialNeed.artists[artistId], result.artists[artistId])
    }

    // --- PairedNeedChange ---

    @Test
    fun `PairedNeedChange applies to the partner artist only`() {
        val effect = StateEffect.PairedNeedChange(secondArtistId, NeedType.CREATIVE_FULFILLMENT, +0.20f)
        val (result, _) = applyResponse(twoArtistWorld, option(effects = listOf(effect)))
        // Partner gets the boost
        assertEquals(0.80f, result.artists[secondArtistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
        // Triggering artist is unaffected
        assertEquals(twoArtistWorld.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value,
            result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
    }

    @Test
    fun `PairedNeedChange clamps partner need at 1f`() {
        val effect = StateEffect.PairedNeedChange(secondArtistId, NeedType.CREATIVE_FULFILLMENT, +0.90f)
        val (result, _) = applyResponse(twoArtistWorld, option(effects = listOf(effect)))
        assertEquals(1.0f, result.artists[secondArtistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
    }

    @Test
    fun `PairedNeedChange for unknown partner is a no-op`() {
        val effect = StateEffect.PairedNeedChange("unknown_artist", NeedType.CREATIVE_FULFILLMENT, +0.50f)
        val (result, _) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld, result)
    }

    // --- Effect ordering ---

    @Test
    fun `multiple effects applied in order`() {
        val effects = listOf(
            StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.10f),
            StateEffect.NeedChange(artistId, NeedType.FINANCIAL_SECURITY, +0.20f),
            StateEffect.LabelFundsChange(delta = 500_00L)
        )
        val (result, _) = applyResponse(baseWorld, option(effects = effects))
        assertEquals(0.70f, result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
        assertEquals(0.60f, result.artists[artistId]!!.needs[NeedType.FINANCIAL_SECURITY]!!.value, 0.001f)
        assertEquals(baseWorld.label.funds + 500_00L, result.label.funds)
    }

    // --- AdvanceNegotiation ---

    private val prospect = ProspectState(
        id = "p0",
        name = "Test Prospect",
        genre = "pop",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.4f, 0.5f),
        signabilityScore = 0.6f
    )

    private val worldWithProspect = baseWorld.copy(prospects = mapOf("p0" to prospect))

    @Test
    fun `AdvanceNegotiation sets round 1 in activeNegotiations`() {
        val effect = StateEffect.AdvanceNegotiation("p0")
        val (result, _) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        assertEquals(1, result.activeNegotiations["p0"])
    }

    @Test
    fun `AdvanceNegotiation emits a NegotiationRound event`() {
        val effect = StateEffect.AdvanceNegotiation("p0")
        val (_, events) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        val negEvent = events.filterIsInstance<SimEvent.NegotiationRound>().firstOrNull()
        assertNotNull(negEvent)
        assertEquals("p0", negEvent!!.prospectId)
        assertEquals(1, negEvent.round)
    }

    @Test
    fun `AdvanceNegotiation second call increments round to 2`() {
        val worldRound1 = worldWithProspect.copy(activeNegotiations = mapOf("p0" to 1))
        val effect = StateEffect.AdvanceNegotiation("p0")
        val (result, events) = applyResponse(worldRound1, option(effects = listOf(effect)))
        assertEquals(2, result.activeNegotiations["p0"])
        assertEquals(2, events.filterIsInstance<SimEvent.NegotiationRound>().first().round)
    }

    @Test
    fun `AdvanceNegotiation for unknown prospect is a no-op`() {
        val effect = StateEffect.AdvanceNegotiation("unknown")
        val (result, events) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld, result)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `AdvanceNegotiation for unavailable prospect is a no-op`() {
        val world = worldWithProspect.copy(unavailableProspects = setOf("p0"))
        val effect = StateEffect.AdvanceNegotiation("p0")
        val (result, events) = applyResponse(world, option(effects = listOf(effect)))
        assertEquals(world, result)
        assertTrue(events.isEmpty())
    }

    // --- SignArtist ---

    @Test
    fun `SignArtist moves prospect to artists and removes from prospects`() {
        val effect = StateEffect.SignArtist("p0")
        val (result, _) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        assertFalse("p0 should be removed from prospects", "p0" in result.prospects)
        assertTrue("signed artist should be in world.artists", result.artists.values.any { it.name == prospect.name })
    }

    @Test
    fun `SignArtist adds artist to label rosterIds`() {
        val effect = StateEffect.SignArtist("p0")
        val (result, _) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        val signedId = "signed_p0"
        assertTrue("$signedId should be in rosterIds", signedId in result.label.rosterIds)
    }

    @Test
    fun `SignArtist creates a contract starting at currentDay`() {
        val world = worldWithProspect.copy(currentDay = 42)
        val effect = StateEffect.SignArtist("p0")
        val (result, _) = applyResponse(world, option(effects = listOf(effect)))
        val contract = result.contracts["contract_p0"]
        assertNotNull(contract)
        assertEquals(42, contract!!.startDay)
        assertEquals(42 + 180, contract.expiryDay)
    }

    @Test
    fun `SignArtist initializes all NeedTypes`() {
        val effect = StateEffect.SignArtist("p0")
        val (result, _) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        val artist = result.artists["signed_p0"]!!
        assertEquals(NeedType.entries.size, artist.needs.size)
    }

    @Test
    fun `SignArtist high-volatility prospect starts with lower needs`() {
        val highVol = prospect.copy(dimensions = prospect.dimensions.copy(volatility = 0.9f))
        val world = baseWorld.copy(prospects = mapOf("p0" to highVol))
        val (result, _) = applyResponse(world, option(effects = listOf(StateEffect.SignArtist("p0"))))
        val avgNeed = result.artists["signed_p0"]!!.needs.values.map { it.value }.average()
        val lowVol = prospect.copy(dimensions = prospect.dimensions.copy(volatility = 0.1f))
        val world2 = baseWorld.copy(prospects = mapOf("p0" to lowVol))
        val (result2, _) = applyResponse(world2, option(effects = listOf(StateEffect.SignArtist("p0"))))
        val avgNeed2 = result2.artists["signed_p0"]!!.needs.values.map { it.value }.average()
        assertTrue("High-volatility needs ($avgNeed) should be lower than low-volatility ($avgNeed2)", avgNeed < avgNeed2)
    }

    @Test
    fun `SignArtist on unknown prospect is a no-op`() {
        val effect = StateEffect.SignArtist("unknown")
        val (result, events) = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld.artists, result.artists)
        assertTrue(events.isEmpty())
    }

    // --- NegotiationFailed ---

    @Test
    fun `NegotiationFailed adds prospect to unavailableProspects`() {
        val world = worldWithProspect.copy(activeNegotiations = mapOf("p0" to 2))
        val effect = StateEffect.NegotiationFailed("p0")
        val (result, _) = applyResponse(world, option(effects = listOf(effect)))
        assertTrue("p0 in unavailableProspects", "p0" in result.unavailableProspects)
    }

    @Test
    fun `NegotiationFailed clears activeNegotiations entry`() {
        val world = worldWithProspect.copy(activeNegotiations = mapOf("p0" to 2))
        val effect = StateEffect.NegotiationFailed("p0")
        val (result, _) = applyResponse(world, option(effects = listOf(effect)))
        assertFalse("p0 should be removed from activeNegotiations", "p0" in result.activeNegotiations)
    }

    @Test
    fun `NegotiationFailed emits no events`() {
        val effect = StateEffect.NegotiationFailed("p0")
        val (_, events) = applyResponse(worldWithProspect, option(effects = listOf(effect)))
        assertTrue(events.isEmpty())
    }
}
