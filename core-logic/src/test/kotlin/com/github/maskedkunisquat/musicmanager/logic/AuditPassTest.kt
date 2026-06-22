package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.ai.StubAiProvider
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditPassTest {

    private val provider = StubAiProvider()

    private fun blankWorld(artists: Map<String, ArtistState> = emptyMap()) = SimWorld(
        seed = 0L,
        currentDay = 10,
        artists = artists,
        label = LabelState(
            funds = 5_000_000L,
            reputation = ReputationCommunity.entries.associateWith { 0.5f },
            rosterIds = artists.keys.toSet()
        ),
        market = MarketState(emptyMap()),
        contracts = emptyMap()
    )

    private fun stubArtist(id: String, confidence: Float = 0.5f, loyalty: Float = 0.5f) = ArtistState(
        id = id,
        name = "Test $id",
        genre = "indie-rock",
        dimensions = ArtistDimensions(confidence, 0.5f, 0.5f, loyalty),
        needs = NeedType.entries.associateWith { NeedState(it, 0.7f, 0.02f) },
        activeWants = emptyList(),
        contractId = null
    )

    // --- NeedUrgent subject rotation ---

    @Test
    fun `NeedUrgent subjects differ across artist IDs for same need type`() {
        val world = blankWorld()
        val subjects = (0..19).map { i ->
            runBlocking {
                provider.generateEmail(
                    SimEvent.NeedUrgent("artist_probe_$i", NeedType.CREATIVE_FULFILLMENT, 0.5f, 0),
                    world
                ).subject
            }
        }
        assertTrue(
            "Expected at least 2 distinct subjects for CREATIVE_FULFILLMENT across 20 artist IDs",
            subjects.distinct().size >= 2
        )
    }

    @Test
    fun `NeedUrgent subject is deterministic for the same artist ID`() {
        val world = blankWorld()
        val first = runBlocking {
            provider.generateEmail(
                SimEvent.NeedUrgent("artist_stable", NeedType.RECOGNITION, 0.5f, 0), world
            ).subject
        }
        val second = runBlocking {
            provider.generateEmail(
                SimEvent.NeedUrgent("artist_stable", NeedType.RECOGNITION, 0.5f, 0), world
            ).subject
        }
        assertEquals("Same artist ID must produce the same subject each time", first, second)
    }

    // --- ContractExpiring tension arc ---

    @Test
    fun `ContractExpiring prose escalates across daysRemaining tiers`() {
        val id = "a0"
        val world = blankWorld(mapOf(id to stubArtist(id)))
        fun body(days: Int) = runBlocking {
            provider.generateEmail(SimEvent.ContractExpiring(id, "c0", days, 0), world).body
        }
        val casual = body(28)
        val warn = body(14)
        val urgent = body(5)
        assertFalse("Casual (28d) and warn (14d) prose must differ", casual == warn)
        assertFalse("Warn (14d) and urgent (5d) prose must differ", warn == urgent)
        assertFalse("Casual (28d) and urgent (5d) prose must differ", casual == urgent)
    }

    @Test
    fun `ContractExpiring subjects differ across tiers`() {
        val id = "a0"
        val world = blankWorld(mapOf(id to stubArtist(id)))
        fun subject(days: Int) = runBlocking {
            provider.generateEmail(SimEvent.ContractExpiring(id, "c0", days, 0), world).subject
        }
        val subjects = setOf(subject(28), subject(14), subject(5))
        assertTrue("Expected at least 2 distinct subjects across ContractExpiring tiers", subjects.size >= 2)
    }

    // --- Dimension reference check ---

    @Test
    fun `NeedUrgent body differs between low-confidence and high-confidence artists`() {
        val id = "a0"
        val lowConfWorld = blankWorld(mapOf(id to stubArtist(id, confidence = 0.20f)))
        val highConfWorld = blankWorld(mapOf(id to stubArtist(id, confidence = 0.70f)))
        val lowBody = runBlocking {
            provider.generateEmail(SimEvent.NeedUrgent(id, NeedType.AUTONOMY, 0.5f, 0), lowConfWorld).body
        }
        val highBody = runBlocking {
            provider.generateEmail(SimEvent.NeedUrgent(id, NeedType.AUTONOMY, 0.5f, 0), highConfWorld).body
        }
        assertNotEquals(
            "Low-confidence (0.20) and high-confidence (0.70) artists must produce different email bodies",
            lowBody,
            highBody
        )
    }
}
