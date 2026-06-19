package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ScoutState
import com.github.maskedkunisquat.musicmanager.logic.sim.SCOUT_REPORT_INTERVAL
import com.github.maskedkunisquat.musicmanager.logic.sim.tickScouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScoutTickerTest {

    private fun prospect(id: String, genre: String = "indie-rock") = ProspectState(
        id = id,
        name = "Test Prospect $id",
        genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        signabilityScore = 0.5f
    )

    private fun scout(id: String, focusGenres: Set<String> = setOf("indie-rock"), lastReportDay: Int = 0) =
        ScoutState(id = id, name = "Scout $id", focusGenres = focusGenres, lastReportDay = lastReportDay)

    private val prospects = mapOf("p0" to prospect("p0"), "p1" to prospect("p1", "pop"))

    // --- Interval ---

    @Test
    fun `scout fires when currentDay minus lastReportDay reaches the interval`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        assertTrue(events.any { it is SimEvent.ScoutReport })
    }

    @Test
    fun `scout does not fire before the interval is reached`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL - 1, prospects, Random(42))
        assertTrue(events.none { it is SimEvent.ScoutReport })
    }

    @Test
    fun `lastReportDay is updated after a report fires`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (updatedScouts, _) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        assertEquals(SCOUT_REPORT_INTERVAL, updatedScouts["s0"]!!.lastReportDay)
    }

    @Test
    fun `lastReportDay unchanged when scout does not fire`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (updatedScouts, _) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL - 1, prospects, Random(42))
        assertEquals(0, updatedScouts["s0"]!!.lastReportDay)
    }

    // --- Multi-scout ---

    @Test
    fun `multiple scouts can fire on the same tick`() {
        val scouts = mapOf(
            "s0" to scout("s0", lastReportDay = 0),
            "s1" to scout("s1", lastReportDay = 0)
        )
        val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        assertEquals(2, events.filterIsInstance<SimEvent.ScoutReport>().size)
    }

    @Test
    fun `staggered scouts fire on different ticks`() {
        val scouts = mapOf(
            "s0" to scout("s0", lastReportDay = 0),
            "s1" to scout("s1", lastReportDay = 4)  // fires 4 ticks later
        )
        val (_, eventsAtInterval) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        val reportIds = eventsAtInterval.filterIsInstance<SimEvent.ScoutReport>().map { it.scoutId }
        assertTrue("s0" in reportIds)
        assertFalse("s1 should not fire yet", "s1" in reportIds)
    }

    // --- Prospect selection ---

    @Test
    fun `ScoutReport references a valid prospect ID`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        val report = events.filterIsInstance<SimEvent.ScoutReport>().first()
        assertTrue("prospectId ${report.prospectId} not in prospects", report.prospectId in prospects)
    }

    @Test
    fun `focus genre weighting favours matching prospects over many seeds`() {
        // s0 focuses on indie-rock; p0 is indie-rock, p1 is pop
        val scouts = mapOf("s0" to scout("s0", focusGenres = setOf("indie-rock"), lastReportDay = 0))
        val focusReports = (0..200).count { seed ->
            val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(seed))
            events.filterIsInstance<SimEvent.ScoutReport>().any { it.prospectId == "p0" }
        }
        val total = 201
        assertTrue("Focus genre should dominate > 70% of picks; got $focusReports/$total", focusReports > total * 7 / 10)
    }

    // --- Edge cases ---

    @Test
    fun `empty prospects returns no events`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0))
        val (_, events) = tickScouts(scouts, currentDay = SCOUT_REPORT_INTERVAL, emptyMap(), Random(42))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `empty scouts returns no events`() {
        val (_, events) = tickScouts(emptyMap(), currentDay = SCOUT_REPORT_INTERVAL, prospects, Random(42))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `tickScouts is deterministic for identical inputs`() {
        val scouts = mapOf("s0" to scout("s0", lastReportDay = 0), "s1" to scout("s1", lastReportDay = 0))
        val a = tickScouts(scouts, SCOUT_REPORT_INTERVAL, prospects, Random(7))
        val b = tickScouts(scouts, SCOUT_REPORT_INTERVAL, prospects, Random(7))
        assertEquals(a, b)
    }

    // --- WorldInitializer integration ---

    @Test
    fun `world initialized with exactly 2 scouts`() {
        val world = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(42L)
        assertEquals(2, world.scouts.size)
    }

    @Test
    fun `scout IDs do not collide with artist or prospect IDs`() {
        val world = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(42L)
        val otherIds = world.artists.keys + world.prospects.keys
        for (id in world.scouts.keys) {
            assertFalse("Scout ID $id collides with artist or prospect", id in otherIds)
        }
    }

    @Test
    fun `scouts are deterministic for same seed`() {
        val a = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(55L).scouts
        val b = com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(55L).scouts
        assertEquals(a, b)
    }

    @Test
    fun `ScoutReport emitted after 8 engine ticks from fresh world`() {
        val engine = com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine()
        val (_, events) = engine.tickN(
            com.github.maskedkunisquat.musicmanager.logic.sim.WorldInitializer.initializeWorld(1L),
            ticks = SCOUT_REPORT_INTERVAL
        )
        assertTrue(
            "Expected at least one ScoutReport after $SCOUT_REPORT_INTERVAL ticks",
            events.any { it is SimEvent.ScoutReport }
        )
    }
}
