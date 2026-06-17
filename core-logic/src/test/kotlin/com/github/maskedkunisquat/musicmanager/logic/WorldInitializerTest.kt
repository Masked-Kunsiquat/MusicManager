package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
