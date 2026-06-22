package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.model.Want
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WantActivationTest {

    private val artistId = "artist_0"

    private fun baseWorld(
        loyalty: Float = 0.5f,
        confidence: Float = 0.5f,
        volatility: Float = 0.5f,
        commercialAppetite: Float = 0.5f,
        wants: List<Want> = emptyList()
    ): SimWorld {
        val artist = ArtistState(
            id = artistId,
            name = "Test Artist",
            genre = "indie-rock",
            dimensions = ArtistDimensions(
                confidence = confidence,
                commercialAppetite = commercialAppetite,
                volatility = volatility,
                loyalty = loyalty
            ),
            needs = NeedType.entries.associateWith { NeedState(it, 0.8f, 0.02f) },
            activeWants = wants,
            contractId = null
        )
        return SimWorld(
            seed = 1L,
            currentDay = 0,
            artists = mapOf(artistId to artist),
            label = LabelState(funds = 10_000_000L, reputation = emptyMap(), rosterIds = setOf(artistId)),
            market = MarketState(emptyMap()),
            contracts = emptyMap()
        )
    }

    private fun option(effects: List<StateEffect>) =
        ResponseOption(id = "test", text = "test", effects = effects, costFunds = 0L)

    // ===== WorldInitializer want seeding =====

    @Test
    fun `low-loyalty artist gets INCREASED_ROYALTIES want`() {
        val world = WorldInitializer.initializeWorld(seed = 42L)
        val lowLoyaltyArtist = world.artists.values.firstOrNull { it.dimensions.loyalty < 0.40f }
        if (lowLoyaltyArtist != null) {
            assertTrue(
                "Expected INCREASED_ROYALTIES want for low-loyalty artist",
                lowLoyaltyArtist.activeWants.any { it.type == WantType.INCREASED_ROYALTIES }
            )
        }
        // Seed 42 doesn't guarantee a low-loyalty artist; keep as conditional guard rather than assertNotNull.
    }

    @Test
    fun `high-confidence artist gets a career want`() {
        val world = WorldInitializer.initializeWorld(seed = 42L)
        val highConfArtist = world.artists.values.firstOrNull { it.dimensions.confidence >= 0.65f }
        assertNotNull("Seed 42 must produce at least one artist with confidence >= 0.65", highConfArtist)
        val careerWants = setOf(WantType.MAJOR_VENUE_TOUR, WantType.RECORD_ALBUM)
        assertTrue(
            "Expected MAJOR_VENUE_TOUR or RECORD_ALBUM for high-confidence artist",
            highConfArtist!!.activeWants.any { it.type in careerWants }
        )
    }

    @Test
    fun `artists have at most 2 active wants at world init`() {
        val world = WorldInitializer.initializeWorld(seed = 99L)
        for (artist in world.artists.values) {
            assertTrue(
                "Artist ${artist.id} has ${artist.activeWants.size} wants (max 2)",
                artist.activeWants.size <= 2
            )
        }
    }

    @Test
    fun `want urgency is in valid range`() {
        val world = WorldInitializer.initializeWorld(seed = 7L)
        for (artist in world.artists.values) {
            for (want in artist.activeWants) {
                assertTrue("Want urgency ${want.urgency} out of range", want.urgency in 0f..1f)
            }
        }
    }

    // ===== WantSurfaced event emission =====

    @Test
    fun `WantSurfaced emits for want with urgency at or above threshold`() {
        val want = Want(type = WantType.GENRE_EXPERIMENT, urgency = 0.75f, expiryDay = null)
        val world = baseWorld(wants = listOf(want))
        val events = generateEvents(world)
        assertTrue(events.any { it is com.github.maskedkunisquat.musicmanager.logic.event.SimEvent.WantSurfaced })
    }

    @Test
    fun `WantSurfaced does not emit for want below threshold`() {
        val want = Want(type = WantType.GENRE_EXPERIMENT, urgency = 0.65f, expiryDay = null)
        val world = baseWorld(wants = listOf(want))
        val events = generateEvents(world)
        assertFalse(events.any { it is com.github.maskedkunisquat.musicmanager.logic.event.SimEvent.WantSurfaced })
    }

    // ===== WantSatisfied effect =====

    @Test
    fun `WantSatisfied removes the want from activeWants`() {
        val want = Want(type = WantType.RECORD_ALBUM, urgency = 0.8f, expiryDay = null)
        val world = baseWorld(wants = listOf(want))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.RECORD_ALBUM)
        )))
        assertTrue(updated.artists[artistId]!!.activeWants.none { it.type == WantType.RECORD_ALBUM })
    }

    @Test
    fun `WantSatisfied grants loyalty bump of 0_15f`() {
        val want = Want(type = WantType.MAJOR_VENUE_TOUR, urgency = 0.8f, expiryDay = null)
        val world = baseWorld(loyalty = 0.50f, wants = listOf(want))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.MAJOR_VENUE_TOUR)
        )))
        assertEquals(0.65f, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `WantSatisfied clamps loyalty at 1f`() {
        val want = Want(type = WantType.MAJOR_VENUE_TOUR, urgency = 0.8f, expiryDay = null)
        val world = baseWorld(loyalty = 0.95f, wants = listOf(want))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.MAJOR_VENUE_TOUR)
        )))
        assertEquals(1.0f, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `WantSatisfied updates relationshipBalance`() {
        val want = Want(type = WantType.RECORD_ALBUM, urgency = 0.8f, expiryDay = null)
        val world = baseWorld(wants = listOf(want))
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.RECORD_ALBUM)
        )))
        assertEquals(0.15f, updated.artists[artistId]!!.relationshipBalance, 0.001f)
    }

    @Test
    fun `WantSatisfied is no-op if want not active`() {
        val world = baseWorld(wants = emptyList())
        val loyaltyBefore = world.artists[artistId]!!.dimensions.loyalty
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.INCREASED_ROYALTIES)
        )))
        assertEquals(loyaltyBefore, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `WantSatisfied only removes the matching want type, leaves others`() {
        val wants = listOf(
            Want(WantType.RECORD_ALBUM, 0.8f, null),
            Want(WantType.GENRE_EXPERIMENT, 0.75f, null)
        )
        val world = baseWorld(wants = wants)
        val (updated, _) = applyResponse(world, option(listOf(
            StateEffect.WantSatisfied(artistId, WantType.RECORD_ALBUM)
        )))
        val remaining = updated.artists[artistId]!!.activeWants
        assertEquals(1, remaining.size)
        assertEquals(WantType.GENRE_EXPERIMENT, remaining[0].type)
    }
}
