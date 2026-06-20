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
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.RIVAL_POACH_THRESHOLD
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalSystemTest {

    private val artistId = "a0"
    private val contractId = "c0"

    private fun baseArtist(loyalty: Float = 0.6f, balance: Float = 0f) = ArtistState(
        id = artistId, name = "Test Artist", genre = "indie-rock",
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, loyalty),
        needs = NeedType.entries.associateWith { NeedState(it, 0.8f, 0.02f) },
        activeWants = emptyList(),
        contractId = contractId,
        relationshipBalance = balance
    )

    private fun baseContract(expiryDay: Int = 200) = Contract(
        id = contractId, artistId = artistId,
        startDay = 0, expiryDay = expiryDay,
        revenueSplit = RevenueSplit(50), creativeControl = CreativeControl.SHARED
    )

    private fun baseWorld(artist: ArtistState = baseArtist(), contract: Contract = baseContract()) = SimWorld(
        seed = 1L, currentDay = 10,
        artists = mapOf(artistId to artist),
        label = LabelState(
            funds = 5_000_000L,
            reputation = ReputationCommunity.entries.associateWith { 0.5f },
            rosterIds = setOf(artistId)
        ),
        market = MarketState(emptyMap()),
        contracts = mapOf(contractId to contract)
    )

    private fun option(effects: List<StateEffect>, cost: Long = 0L) =
        ResponseOption(id = "opt_${System.nanoTime()}", text = "Option", effects = effects, costFunds = cost)

    // ===== relationshipBalance tracking =====

    @Test
    fun `RelationshipChange positive delta increases relationshipBalance`() {
        val world = baseWorld()
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RelationshipChange(artistId, +0.20f))))
        assertEquals(0.20f, updated.artists[artistId]!!.relationshipBalance, 0.001f)
    }

    @Test
    fun `RelationshipChange negative delta decreases relationshipBalance`() {
        val world = baseWorld(baseArtist(balance = 0.5f))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RelationshipChange(artistId, -0.30f))))
        assertEquals(0.20f, updated.artists[artistId]!!.relationshipBalance, 0.001f)
    }

    @Test
    fun `relationshipBalance accumulates across multiple RelationshipChange effects`() {
        var world = baseWorld()
        world = applyResponse(world, option(listOf(StateEffect.RelationshipChange(artistId, +0.30f)))).first
        world = applyResponse(world, option(listOf(StateEffect.RelationshipChange(artistId, -0.10f)))).first
        world = applyResponse(world, option(listOf(StateEffect.RelationshipChange(artistId, +0.20f)))).first
        assertEquals(0.40f, world.artists[artistId]!!.relationshipBalance, 0.001f)
    }

    @Test
    fun `relationshipBalance defaults to 0f on existing artists`() {
        assertEquals(0f, baseArtist().relationshipBalance, 0.001f)
    }

    // ===== OpenRenewal =====

    @Test
    fun `OpenRenewal injects RenewalOpened round 1 event`() {
        val world = baseWorld()
        val (_, events) = applyResponse(world, option(listOf(StateEffect.OpenRenewal(artistId, contractId))))
        val renewal = events.filterIsInstance<SimEvent.RenewalOpened>().firstOrNull()
        assertNotNull(renewal)
        assertEquals(1, renewal!!.round)
        assertEquals(artistId, renewal.artistId)
        assertEquals(contractId, renewal.contractId)
    }

    @Test
    fun `OpenRenewal sets activeRenewals round 1 on world`() {
        val world = baseWorld()
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.OpenRenewal(artistId, contractId))))
        assertEquals(1, updated.activeRenewals[artistId])
    }

    @Test
    fun `OpenRenewal is no-op when contractId does not match artist's current contract`() {
        val world = baseWorld()
        val (updated, events) = applyResponse(world, option(listOf(StateEffect.OpenRenewal(artistId, "wrong_contract"))))
        assertTrue(events.isEmpty())
        assertNull(updated.activeRenewals[artistId])
    }

    // ===== AdvanceRenewal =====

    @Test
    fun `AdvanceRenewal injects RenewalOpened for the next round`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 1))
        val (_, events) = applyResponse(world, option(listOf(StateEffect.AdvanceRenewal(artistId, contractId))))
        val renewal = events.filterIsInstance<SimEvent.RenewalOpened>().firstOrNull()
        assertNotNull(renewal)
        assertEquals(2, renewal!!.round)
    }

    @Test
    fun `AdvanceRenewal increments activeRenewals counter`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 2))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.AdvanceRenewal(artistId, contractId))))
        assertEquals(3, updated.activeRenewals[artistId])
    }

    @Test
    fun `AdvanceRenewal is no-op when artistId not in activeRenewals`() {
        val world = baseWorld()
        val (updated, events) = applyResponse(world, option(listOf(StateEffect.AdvanceRenewal(artistId, contractId))))
        assertTrue(events.isEmpty())
        assertNull(updated.activeRenewals[artistId])
    }

    // ===== RenewContract =====

    @Test
    fun `RenewContract creates a new contract with correct terms`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 1))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.RenewContract(artistId, 180, RevenueSplit(55), CreativeControl.SHARED)
        )))
        val newContractId = updated.artists[artistId]!!.contractId
        assertNotNull(newContractId)
        assertFalse(newContractId == contractId)  // new id, not the old one
        val newContract = updated.contracts[newContractId]!!
        assertEquals(55, newContract.revenueSplit.artistPercent)
        assertEquals(CreativeControl.SHARED, newContract.creativeControl)
        assertEquals(10 + 180, newContract.expiryDay)  // currentDay=10 + 180 ticks
    }

    @Test
    fun `RenewContract removes old contract`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 1))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.RenewContract(artistId, 180, RevenueSplit(50), CreativeControl.SHARED)
        )))
        assertFalse(updated.contracts.containsKey(contractId))
    }

    @Test
    fun `RenewContract clears activeRenewals for this artist`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 2))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.RenewContract(artistId, 180, RevenueSplit(50), CreativeControl.SHARED)
        )))
        assertNull(updated.activeRenewals[artistId])
    }

    // ===== RenewalWalked =====

    @Test
    fun `RenewalWalked reduces artist loyalty by 0_2`() {
        val world = baseWorld(baseArtist(loyalty = 0.6f)).copy(activeRenewals = mapOf(artistId to 1))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RenewalWalked(artistId))))
        assertEquals(0.4f, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RenewalWalked clamps loyalty to 0f`() {
        val world = baseWorld(baseArtist(loyalty = 0.1f)).copy(activeRenewals = mapOf(artistId to 1))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RenewalWalked(artistId))))
        assertEquals(0f, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `RenewalWalked clears activeRenewals for this artist`() {
        val world = baseWorld().copy(activeRenewals = mapOf(artistId to 1))
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RenewalWalked(artistId))))
        assertNull(updated.activeRenewals[artistId])
    }

    @Test
    fun `RenewalWalked accelerates rival poach counter to threshold minus 1`() {
        val rivalId = "r0"
        val world = baseWorld().copy(
            activeRenewals = mapOf(artistId to 1),
            rivals = mapOf(rivalId to com.github.maskedkunisquat.musicmanager.logic.model.RivalState(
                id = rivalId, name = "Mercury Sound", genreWeights = mapOf("indie-rock" to 1.0f)
            )),
            rivalPoachTargets = mapOf(rivalId to artistId),
            rivalPoachCounters = mapOf(rivalId to 3)  // mid-pursuit
        )
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RenewalWalked(artistId))))
        assertEquals(RIVAL_POACH_THRESHOLD - 1, updated.rivalPoachCounters[rivalId])
    }

    @Test
    fun `RenewalWalked does not affect rival counter when rival is not targeting this artist`() {
        val rivalId = "r0"
        val world = baseWorld().copy(
            rivals = mapOf(rivalId to com.github.maskedkunisquat.musicmanager.logic.model.RivalState(
                id = rivalId, name = "Mercury Sound", genreWeights = mapOf("indie-rock" to 1.0f)
            )),
            rivalPoachTargets = mapOf(rivalId to "some_other_artist"),
            rivalPoachCounters = mapOf(rivalId to 3)
        )
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.RenewalWalked(artistId))))
        assertEquals(3, updated.rivalPoachCounters[rivalId])
    }

    // ===== Full round-trip: ContractExpiring → OpenRenewal → AdvanceRenewal → RenewContract =====

    @Test
    fun `full three-round renewal flow produces final contract`() {
        var world = baseWorld()

        // Round 1: OpenRenewal
        val (w1, e1) = applyResponse(world, option(listOf(StateEffect.OpenRenewal(artistId, contractId))))
        world = w1
        assertEquals(1, world.activeRenewals[artistId])
        assertEquals(1, e1.filterIsInstance<SimEvent.RenewalOpened>().first().round)

        // Round 2: AdvanceRenewal
        val (w2, e2) = applyResponse(world, option(listOf(StateEffect.AdvanceRenewal(artistId, contractId))))
        world = w2
        assertEquals(2, world.activeRenewals[artistId])
        assertEquals(2, e2.filterIsInstance<SimEvent.RenewalOpened>().first().round)

        // Round 3: sign
        val (w3, _) = applyResponse(world, option(listOf(
            StateEffect.RenewContract(artistId, 180, RevenueSplit(50), CreativeControl.SHARED)
        )))
        world = w3
        assertNull(world.activeRenewals[artistId])
        assertFalse(world.contracts.containsKey(contractId))
        val newContractId = world.artists[artistId]!!.contractId
        assertNotNull(newContractId)
        assertTrue(world.contracts.containsKey(newContractId))
    }
}
