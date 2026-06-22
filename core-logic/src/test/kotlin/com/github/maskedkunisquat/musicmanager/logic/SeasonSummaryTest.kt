package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonFacts
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.SeasonSummaryEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonSummaryTest {

    private fun artist(id: String) = ArtistState(
        id = id,
        name = "Artist $id",
        genre = "indie-rock",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        needs = mapOf(NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.7f, 0.02f)),
        activeWants = emptyList(),
        contractId = null
    )

    private val startReputation = ReputationCommunity.entries.associate { it.name to 0.4f }
    private val endReputation = ReputationCommunity.entries.associate { it to 0.5f }

    private fun baseWorld(
        artists: Map<String, ArtistState>,
        funds: Long = 120_000_00L,
        startFunds: Long = 100_000_00L,
        rep: Map<ReputationCommunity, Float> = endReputation
    ): SimWorld = SimWorld(
        seed = 1L,
        currentDay = 180,
        artists = artists,
        label = LabelState(
            funds = funds,
            reputation = rep,
            rosterIds = artists.keys,
            tasteVector = emptyMap()
        ),
        market = MarketState(genreTrends = mapOf("indie-rock" to 0.5f)),
        contracts = emptyMap(),
        season = SeasonState(
            seasonNumber = 1,
            seasonStartTick = 0,
            seasonEndTick = 180,
            startFunds = startFunds,
            startReputation = startReputation
        )
    )

    private fun emptyFacts() = SeasonFacts(
        rivalPoachArtistIds = emptySet(),
        renewalWalkedArtistIds = emptySet(),
        deadlinesMet = 0,
        deadlinesMissed = 0
    )

    // --- artistsRetained ---

    @Test
    fun `artistsRetained equals current world artist count`() {
        val world = baseWorld(mapOf("a1" to artist("a1"), "a2" to artist("a2")))
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(2, summary.artistsRetained)
    }

    // --- artistsLost ---

    @Test
    fun `artistsLost counts distinct rival poach ids`() {
        val world = baseWorld(mapOf("a1" to artist("a1")))
        val facts = emptyFacts().copy(rivalPoachArtistIds = setOf("a2", "a3"))
        val summary = SeasonSummaryEvaluator.evaluate(world, facts)
        assertEquals(2, summary.artistsLost)
    }

    @Test
    fun `artistsLost counts distinct renewal walked ids`() {
        val world = baseWorld(mapOf("a1" to artist("a1")))
        val facts = emptyFacts().copy(renewalWalkedArtistIds = setOf("a2"))
        val summary = SeasonSummaryEvaluator.evaluate(world, facts)
        assertEquals(1, summary.artistsLost)
    }

    @Test
    fun `artistsLost deduplicates across poach and walked sets`() {
        // Same artist ID in both sets still counts as one lost artist.
        val world = baseWorld(mapOf("a1" to artist("a1")))
        val facts = emptyFacts().copy(
            rivalPoachArtistIds = setOf("a2"),
            renewalWalkedArtistIds = setOf("a2")
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, facts)
        assertEquals(1, summary.artistsLost)
    }

    // --- deadline counts ---

    @Test
    fun `deadlinesMet and deadlinesMissed pass through from facts`() {
        val world = baseWorld(mapOf("a1" to artist("a1")))
        val facts = emptyFacts().copy(deadlinesMet = 3, deadlinesMissed = 1)
        val summary = SeasonSummaryEvaluator.evaluate(world, facts)
        assertEquals(3, summary.deadlinesMet)
        assertEquals(1, summary.deadlinesMissed)
    }

    // --- fundsNet ---

    @Test
    fun `fundsNet is positive when funds increased`() {
        val world = baseWorld(
            artists = mapOf("a1" to artist("a1")),
            funds = 120_000_00L,
            startFunds = 100_000_00L
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(20_000_00L, summary.fundsNet)
    }

    @Test
    fun `fundsNet is negative when funds decreased`() {
        val world = baseWorld(
            artists = mapOf("a1" to artist("a1")),
            funds = 80_000_00L,
            startFunds = 100_000_00L
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(-20_000_00L, summary.fundsNet)
    }

    @Test
    fun `fundsNet is zero when funds unchanged`() {
        val world = baseWorld(
            artists = mapOf("a1" to artist("a1")),
            funds = 100_000_00L,
            startFunds = 100_000_00L
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(0L, summary.fundsNet)
    }

    // --- reputationDeltas ---

    @Test
    fun `reputationDeltas reflects per-community change`() {
        val world = baseWorld(
            artists = mapOf("a1" to artist("a1")),
            rep = ReputationCommunity.entries.associate { it to 0.5f }
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        // All communities went from 0.4 to 0.5 = +0.1 delta each
        for (community in ReputationCommunity.entries) {
            assertEquals(0.1f, summary.reputationDeltas[community.name]!!, 0.001f)
        }
    }

    // --- empty season ---

    @Test
    fun `empty season produces all-zero summary without crashing`() {
        val world = baseWorld(
            artists = mapOf("a1" to artist("a1")),
            funds = 100_000_00L,
            startFunds = 100_000_00L,
            rep = ReputationCommunity.entries.associate { it to 0.4f }
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(0, summary.artistsLost)
        assertEquals(0, summary.deadlinesMet)
        assertEquals(0, summary.deadlinesMissed)
        assertEquals(0L, summary.fundsNet)
        for ((_, delta) in summary.reputationDeltas) {
            assertEquals(0f, delta, 0.001f)
        }
    }

    @Test
    fun `seasonNumber propagates from world`() {
        val world = baseWorld(mapOf("a1" to artist("a1"))).copy(
            season = baseWorld(mapOf("a1" to artist("a1"))).season.copy(seasonNumber = 3)
        )
        val summary = SeasonSummaryEvaluator.evaluate(world, emptyFacts())
        assertEquals(3, summary.seasonNumber)
    }
}
