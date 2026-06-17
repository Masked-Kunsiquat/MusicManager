package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        val result = applyResponse(baseWorld, option(cost = 500_00L))
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
        val result = applyResponse(baseWorld, option(cost = 0L))
        assertEquals(baseWorld.label.funds, result.label.funds)
    }

    // --- NeedChange ---

    @Test
    fun `NeedChange increases need value correctly`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.20f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(0.80f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange clamps at 1f on overflow`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.90f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(1.0f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange clamps at 0f on underflow`() {
        val effect = StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, -1.0f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        val need = result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!
        assertEquals(0.0f, need.value, 0.001f)
    }

    @Test
    fun `NeedChange for unknown artist is a no-op`() {
        val effect = StateEffect.NeedChange("unknown_artist", NeedType.CREATIVE_FULFILLMENT, +0.5f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld, result)
    }

    // --- LabelFundsChange ---

    @Test
    fun `LabelFundsChange adds income to label funds`() {
        val effect = StateEffect.LabelFundsChange(delta = 1_000_00L)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(baseWorld.label.funds + 1_000_00L, result.label.funds)
    }

    // --- RelationshipChange ---

    @Test
    fun `RelationshipChange updates loyalty correctly`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = +0.20f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(0.70f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RelationshipChange clamps loyalty at 1f`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = +0.80f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(1.0f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RelationshipChange clamps loyalty at 0f`() {
        val effect = StateEffect.RelationshipChange(artistId, delta = -1.0f)
        val result = applyResponse(baseWorld, option(effects = listOf(effect)))
        assertEquals(0.0f, result.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    // --- Effect ordering ---

    @Test
    fun `multiple effects applied in order`() {
        val effects = listOf(
            StateEffect.NeedChange(artistId, NeedType.CREATIVE_FULFILLMENT, +0.10f),
            StateEffect.NeedChange(artistId, NeedType.FINANCIAL_SECURITY, +0.20f),
            StateEffect.LabelFundsChange(delta = 500_00L)
        )
        val result = applyResponse(baseWorld, option(effects = effects))
        assertEquals(0.70f, result.artists[artistId]!!.needs[NeedType.CREATIVE_FULFILLMENT]!!.value, 0.001f)
        assertEquals(0.60f, result.artists[artistId]!!.needs[NeedType.FINANCIAL_SECURITY]!!.value, 0.001f)
        assertEquals(baseWorld.label.funds + 500_00L, result.label.funds)
    }
}
