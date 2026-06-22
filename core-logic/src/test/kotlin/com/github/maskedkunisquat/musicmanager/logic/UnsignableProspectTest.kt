package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsignableProspectTest {

    private val prospectId = "p_whale"

    private val unsignable = ProspectState(
        id = prospectId,
        name = "Wild Ghosts",
        genre = "indie-rock",
        dimensions = ArtistDimensions(0.9f, 0.1f, 0.1f, 0.2f),
        signabilityScore = 0.95f,
        signability = SignabilityType.UNSIGNABLE
    )

    private val normalProspect = ProspectState(
        id = "p_normal",
        name = "Young Stars",
        genre = "pop",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        signabilityScore = 0.6f,
        signability = SignabilityType.NORMAL
    )

    private fun baseWorld(extraNegotiations: Map<String, Int> = emptyMap()) = SimWorld(
        seed = 1L, currentDay = 10,
        artists = emptyMap(),
        label = LabelState(
            funds = 1_000_000L,
            reputation = ReputationCommunity.entries.associateWith { 0.5f },
            rosterIds = emptySet()
        ),
        market = MarketState(emptyMap()),
        contracts = emptyMap(),
        prospects = mapOf(prospectId to unsignable, "p_normal" to normalProspect),
        activeNegotiations = extraNegotiations
    )

    private fun option(effects: List<StateEffect>) =
        ResponseOption(id = "opt", text = "Option", effects = effects, costFunds = 0L)

    @Test
    fun `SignArtist on UNSIGNABLE does not sign or remove prospect`() {
        val world = baseWorld()
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.SignArtist(prospectId))))

        assertTrue("Prospect still in pool", updated.prospects.containsKey(prospectId))
        assertFalse("Prospect not added to artists", updated.artists.containsKey("signed_$prospectId"))
    }

    @Test
    fun `SignArtist on UNSIGNABLE clears active negotiation`() {
        val world = baseWorld(extraNegotiations = mapOf(prospectId to 2))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.SignArtist(prospectId))))

        assertNull("Negotiation cleared", updated.activeNegotiations[prospectId])
    }

    @Test
    fun `SignArtist on UNSIGNABLE does not add to unavailableProspects`() {
        val world = baseWorld(extraNegotiations = mapOf(prospectId to 1))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.SignArtist(prospectId))))

        assertFalse("Not in cooldown set", updated.unavailableProspects.contains(prospectId))
    }

    @Test
    fun `UNSIGNABLE prospect can be approached again after failed sign`() {
        var world = baseWorld()

        // Advance negotiation (start talking)
        val (w1, _) = applyResponse(world, option(listOf(StateEffect.AdvanceNegotiation(prospectId))))
        world = w1
        assertEquals(1, world.activeNegotiations[prospectId])

        // Try to sign — should fail silently
        val (w2, _) = applyResponse(world, option(listOf(StateEffect.SignArtist(prospectId))))
        world = w2
        assertNull(world.activeNegotiations[prospectId])
        assertTrue(world.prospects.containsKey(prospectId))
        assertFalse(world.unavailableProspects.contains(prospectId))

        // Can start a new negotiation immediately
        val (w3, e3) = applyResponse(world, option(listOf(StateEffect.AdvanceNegotiation(prospectId))))
        world = w3
        assertEquals(1, world.activeNegotiations[prospectId])
        assertTrue("NegotiationRound injected", e3.isNotEmpty())
    }

    @Test
    fun `SignArtist on NORMAL prospect still works`() {
        val world = baseWorld()
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.SignArtist("p_normal"))))

        assertFalse("Normal prospect removed from pool", updated.prospects.containsKey("p_normal"))
        assertTrue("Normal prospect signed as artist", updated.artists.containsKey("signed_p_normal"))
    }

    @Test
    fun `WorldInitializer seeds exactly one UNSIGNABLE prospect per world`() {
        val worldA = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(seed = 42L)
        val worldB = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(seed = 99L)

        val unsignableCountA = worldA.prospects.values.count { it.signability == SignabilityType.UNSIGNABLE }
        val unsignableCountB = worldB.prospects.values.count { it.signability == SignabilityType.UNSIGNABLE }

        assertEquals("Exactly one whale per world (seed 42)", 1, unsignableCountA)
        assertEquals("Exactly one whale per world (seed 99)", 1, unsignableCountB)
    }

    @Test
    fun `UNSIGNABLE prospect has high signabilityScore`() {
        val world = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(seed = 1L)
        val whale = world.prospects.values.first { it.signability == SignabilityType.UNSIGNABLE }
        assertTrue("Whale signabilityScore >= 0.90", whale.signabilityScore >= 0.90f)
    }
}
