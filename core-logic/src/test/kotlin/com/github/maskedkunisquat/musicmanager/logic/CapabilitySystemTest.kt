package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitySystemTest {

    private val baseLabel = LabelState(
        funds = 10_000_000L,  // $100k — above VIDEO_PRODUCTION gate
        reputation = ReputationCommunity.entries.associateWith { 0.5f },  // above rep gates
        rosterIds = emptySet()
    )

    private fun world(
        label: LabelState = baseLabel,
        capabilityNoticedAt: Map<String, Int> = emptyMap()
    ) = SimWorld(
        seed = 1L,
        currentDay = 10,
        artists = emptyMap(),
        label = label,
        market = MarketState(emptyMap()),
        contracts = emptyMap(),
        capabilityNoticedAt = capabilityNoticedAt
    )

    private fun option(effects: List<StateEffect>, cost: Long = 0L) =
        ResponseOption(id = "opt", text = "Option", effects = effects, costFunds = cost)

    // --- CapabilityUnlockable emission ---

    @Test
    fun `CapabilityUnlockable emitted when all gates are met`() {
        val events = generateEvents(world())
        val types = events.filterIsInstance<SimEvent.CapabilityUnlockable>().map { it.type }.toSet()
        assertTrue(CapabilityType.PUBLICIST in types)
        assertTrue(CapabilityType.IN_HOUSE_BOOKING in types)
        assertTrue(CapabilityType.VIDEO_PRODUCTION in types)
    }

    @Test
    fun `CapabilityUnlockable not emitted when capability already unlocked`() {
        val w = world(label = baseLabel.copy(capabilities = setOf(CapabilityType.PUBLICIST)))
        val events = generateEvents(w)
        assertTrue(events.none {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.PUBLICIST
        })
    }

    @Test
    fun `CapabilityUnlockable not emitted within cooldown window`() {
        val noticed = mapOf(CapabilityType.PUBLICIST.name to 5)  // noticed on day 5, current day 10 → only 5 ticks ago
        val w = world(capabilityNoticedAt = noticed)
        val events = generateEvents(w)
        assertTrue(events.none {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.PUBLICIST
        })
    }

    @Test
    fun `CapabilityUnlockable re-emitted after cooldown expires`() {
        // Day 10, noticed on day -15 → 25 ticks ago, above the 20-tick cooldown
        val noticed = mapOf(CapabilityType.PUBLICIST.name to -15)
        val w = world(capabilityNoticedAt = noticed)
        val events = generateEvents(w)
        assertTrue(events.any {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.PUBLICIST
        })
    }

    @Test
    fun `PUBLICIST not emitted when PRESS rep is below gate`() {
        val lowRep = baseLabel.copy(
            reputation = baseLabel.reputation + (ReputationCommunity.PRESS to 0.3f)
        )
        val events = generateEvents(world(label = lowRep))
        assertTrue(events.none {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.PUBLICIST
        })
    }

    @Test
    fun `IN_HOUSE_BOOKING not emitted when VENUE_BOOKERS rep is below gate`() {
        val lowRep = baseLabel.copy(
            reputation = baseLabel.reputation + (ReputationCommunity.VENUE_BOOKERS to 0.2f)
        )
        val events = generateEvents(world(label = lowRep))
        assertTrue(events.none {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.IN_HOUSE_BOOKING
        })
    }

    @Test
    fun `VIDEO_PRODUCTION not emitted when funds are below gate`() {
        val lowFunds = baseLabel.copy(funds = 3_000_000L)  // $30k < $50k gate
        val events = generateEvents(world(label = lowFunds))
        assertTrue(events.none {
            it is SimEvent.CapabilityUnlockable && it.type == CapabilityType.VIDEO_PRODUCTION
        })
    }

    @Test
    fun `CapabilityUnlockable carries correct costFunds`() {
        val events = generateEvents(world())
        val publicistEvent = events.filterIsInstance<SimEvent.CapabilityUnlockable>()
            .first { it.type == CapabilityType.PUBLICIST }
        assertEquals(1_500_000L, publicistEvent.costFunds)
    }

    // --- SimEngine stamps capabilityNoticedAt ---

    @Test
    fun `SimEngine stamps capabilityNoticedAt when event emitted`() {
        val engine = SimEngine()
        val w = world().copy(currentDay = 0)
        val result = engine.tick(w)
        val noticed = result.world.capabilityNoticedAt
        // All three capabilities should be noticed at day 1 (after tick)
        assertTrue(CapabilityType.PUBLICIST.name in noticed)
        assertTrue(CapabilityType.IN_HOUSE_BOOKING.name in noticed)
        assertTrue(CapabilityType.VIDEO_PRODUCTION.name in noticed)
    }

    @Test
    fun `SimEngine does not re-stamp capabilityNoticedAt when within cooldown`() {
        val engine = SimEngine()
        val noticed = CapabilityType.entries.associateBy({ it.name }, { 0 })
        val w = world(capabilityNoticedAt = noticed).copy(currentDay = 5)
        val result = engine.tick(w)
        // Still within cooldown (5 ticks) — noticed days should be unchanged
        CapabilityType.entries.forEach { type ->
            assertEquals(0, result.world.capabilityNoticedAt[type.name])
        }
    }

    // --- UnlockCapability effect ---

    @Test
    fun `UnlockCapability adds capability to label`() {
        val w = world()
        val effect = StateEffect.UnlockCapability(CapabilityType.PUBLICIST)
        val (result, _) = applyResponse(w, option(listOf(effect)))
        assertTrue(CapabilityType.PUBLICIST in result.label.capabilities)
    }

    @Test
    fun `UnlockCapability is idempotent`() {
        val w = world(label = baseLabel.copy(capabilities = setOf(CapabilityType.PUBLICIST)))
        val effect = StateEffect.UnlockCapability(CapabilityType.PUBLICIST)
        val (result, _) = applyResponse(w, option(listOf(effect)))
        assertEquals(setOf(CapabilityType.PUBLICIST), result.label.capabilities)
    }

    @Test
    fun `UnlockCapability does not affect other capabilities`() {
        val w = world(label = baseLabel.copy(capabilities = setOf(CapabilityType.IN_HOUSE_BOOKING)))
        val effect = StateEffect.UnlockCapability(CapabilityType.PUBLICIST)
        val (result, _) = applyResponse(w, option(listOf(effect)))
        assertTrue(CapabilityType.PUBLICIST in result.label.capabilities)
        assertTrue(CapabilityType.IN_HOUSE_BOOKING in result.label.capabilities)
    }

    // --- LabelState backward compat ---

    @Test
    fun `LabelState capabilities defaults to empty set`() {
        assertTrue(baseLabel.capabilities.isEmpty())
    }
}
