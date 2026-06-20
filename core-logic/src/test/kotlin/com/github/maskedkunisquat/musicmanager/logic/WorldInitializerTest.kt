package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirrors worldJson in core-data/WorldSerializer.kt — must stay in sync if that config changes.
private val testJson = Json { ignoreUnknownKeys = true }

class WorldInitializerTest {

    @Test
    fun `same seed produces identical worlds`() {
        val world1 = WorldInitializer.initializeWorld(42L)
        val world2 = WorldInitializer.initializeWorld(42L)
        assertEquals(world1, world2)
    }

    @Test
    fun `different seeds produce different worlds`() {
        val world1 = WorldInitializer.initializeWorld(1L)
        val world2 = WorldInitializer.initializeWorld(2L)
        assertNotEquals(world1, world2)
    }

    @Test
    fun `world starts at day zero`() {
        assertEquals(0, WorldInitializer.initializeWorld(99L).currentDay)
    }

    @Test
    fun `roster size is between 3 and 5`() {
        val world = WorldInitializer.initializeWorld(7L)
        assertTrue(world.artists.size in 3..5)
    }

    @Test
    fun `all artists start with needs above urgent threshold`() {
        val world = WorldInitializer.initializeWorld(13L)
        for (artist in world.artists.values) {
            for (need in artist.needs.values) {
                assertTrue(
                    "${artist.name} ${need.type} initialized below 0.7 (${need.value})",
                    need.value >= 0.7f
                )
            }
        }
    }

    @Test
    fun `every artist has a valid contract`() {
        val world = WorldInitializer.initializeWorld(21L)
        for (artist in world.artists.values) {
            assertNotNull("${artist.name} missing contractId", artist.contractId)
            assertNotNull("contract ${artist.contractId} not in contracts map",
                world.contracts[artist.contractId])
        }
    }

    // --- Prospects ---

    @Test
    fun `prospect count is between 6 and 10`() {
        val world = WorldInitializer.initializeWorld(7L)
        assertTrue("Expected 6–10 prospects, got ${world.prospects.size}", world.prospects.size in 6..10)
    }

    @Test
    fun `prospect IDs are unique`() {
        val world = WorldInitializer.initializeWorld(42L)
        assertEquals(world.prospects.size, world.prospects.keys.toSet().size)
    }

    @Test
    fun `all prospect signabilityScores are in valid range`() {
        val world = WorldInitializer.initializeWorld(55L)
        for (prospect in world.prospects.values) {
            assertTrue(
                "${prospect.name} signabilityScore out of range: ${prospect.signabilityScore}",
                prospect.signabilityScore in 0f..1f
            )
        }
    }

    @Test
    fun `all prospect signabilityScores are between 0_2 and 0_9`() {
        // WorldInitializer constrains NORMAL prospects to 0.2–0.9 so negotiations are never trivially impossible/trivial.
        // The UNSIGNABLE whale sits at 0.90–1.00 intentionally (scouts surface them constantly).
        val world = WorldInitializer.initializeWorld(77L)
        for (prospect in world.prospects.values) {
            if (prospect.signability == com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType.UNSIGNABLE) continue
            assertTrue(
                "${prospect.name} signabilityScore outside 0.2–0.9: ${prospect.signabilityScore}",
                prospect.signabilityScore in 0.2f..0.9f
            )
        }
    }

    @Test
    fun `prospect genres are valid market genres`() {
        val knownGenres = setOf("indie-rock", "pop", "hip-hop", "electronic", "folk", "r&b")
        val world = WorldInitializer.initializeWorld(33L)
        for (prospect in world.prospects.values) {
            assertTrue(
                "Unknown genre '${prospect.genre}' for prospect ${prospect.name}",
                prospect.genre in knownGenres
            )
        }
    }

    @Test
    fun `prospects are deterministic for same seed`() {
        val a = WorldInitializer.initializeWorld(99L).prospects
        val b = WorldInitializer.initializeWorld(99L).prospects
        assertEquals(a, b)
    }

    @Test
    fun `prospects differ across seeds`() {
        val a = WorldInitializer.initializeWorld(1L).prospects
        val b = WorldInitializer.initializeWorld(2L).prospects
        assertNotEquals(a, b)
    }

    @Test
    fun `no prospect ID collides with artist IDs`() {
        val world = WorldInitializer.initializeWorld(42L)
        val artistIds = world.artists.keys
        for (id in world.prospects.keys) {
            assertTrue("Prospect ID $id collides with an artist ID", id !in artistIds)
        }
    }

    // --- Serialization ---

    @Test
    fun `SimWorld round-trips through JSON losslessly`() {
        val original = WorldInitializer.initializeWorld(42L)
        val json = testJson.encodeToString(SimWorld.serializer(), original)
        val restored = testJson.decodeFromString(SimWorld.serializer(), json)
        assertEquals(original, restored)
    }

    @Test
    fun `prospects survive JSON round-trip`() {
        val original = WorldInitializer.initializeWorld(77L)
        val json = testJson.encodeToString(SimWorld.serializer(), original)
        val restored = testJson.decodeFromString(SimWorld.serializer(), json)
        assertEquals(original.prospects, restored.prospects)
    }

    @Test
    fun `scouts survive JSON round-trip`() {
        val original = WorldInitializer.initializeWorld(55L)
        val json = testJson.encodeToString(SimWorld.serializer(), original)
        val restored = testJson.decodeFromString(SimWorld.serializer(), json)
        assertEquals(original.scouts, restored.scouts)
    }

    @Test
    fun `world without prospects and scouts deserializes from legacy snapshot`() {
        // Snapshots written before 2-A omit prospects/scouts; defaults must kick in.
        val legacy = """{"seed":1,"currentDay":0,"artists":{},"label":{"funds":0,"reputation":{},"rosterIds":[]},"market":{"genreTrends":{}},"contracts":{}}"""
        val world = testJson.decodeFromString(SimWorld.serializer(), legacy)
        assertEquals(emptyMap<String, Any>(), world.prospects)
        assertEquals(emptyMap<String, Any>(), world.scouts)
    }
}
