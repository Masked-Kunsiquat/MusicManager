package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Deadline
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineStatus
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.SimEngine
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import com.github.maskedkunisquat.musicmanager.logic.sim.generateEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonCalendarTest {

    private val artistId = "artist_0"
    private val deadlineId = "deadline:$artistId:ALBUM_RELEASE:1"

    private fun baseWorld(
        currentDay: Int,
        deadlineStatus: DeadlineStatus = DeadlineStatus.PENDING,
        dueTick: Int = 100
    ): SimWorld {
        val deadline = Deadline(
            id = deadlineId,
            artistId = artistId,
            type = DeadlineType.ALBUM_RELEASE,
            dueTick = dueTick,
            status = deadlineStatus
        )
        return SimWorld(
            seed = 1L,
            currentDay = currentDay,
            artists = mapOf(
                artistId to ArtistState(
                    id = artistId,
                    name = "Test Artist",
                    genre = "indie-rock",
                    dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
                    needs = mapOf(
                        NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.6f, 0.02f)
                    ),
                    activeWants = emptyList(),
                    contractId = null
                )
            ),
            label = LabelState(
                funds = 100_000_00L,
                reputation = ReputationCommunity.entries.associateWith { 0.4f },
                rosterIds = setOf(artistId),
                tasteVector = emptyMap()
            ),
            market = MarketState(genreTrends = mapOf("indie-rock" to 0.5f)),
            contracts = emptyMap(),
            deadlines = mapOf(deadlineId to deadline),
            season = SeasonState(seasonNumber = 1, seasonStartTick = 0, seasonEndTick = 180)
        )
    }

    private fun option(effects: List<StateEffect>) =
        ResponseOption(id = "test", text = "test", effects = effects)

    // --- DeadlineApproaching emission ---

    @Test
    fun `DeadlineApproaching fires at 20 ticks before dueTick`() {
        val world = baseWorld(currentDay = 80, dueTick = 100)
        val events = generateEvents(world)
        assertTrue(events.any { it is SimEvent.DeadlineApproaching && (it as SimEvent.DeadlineApproaching).ticksRemaining == 20 })
    }

    @Test
    fun `DeadlineApproaching fires at 10 ticks before dueTick`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val events = generateEvents(world)
        assertTrue(events.any { it is SimEvent.DeadlineApproaching && (it as SimEvent.DeadlineApproaching).ticksRemaining == 10 })
    }

    @Test
    fun `DeadlineApproaching fires at 5 ticks before dueTick`() {
        val world = baseWorld(currentDay = 95, dueTick = 100)
        val events = generateEvents(world)
        assertTrue(events.any { it is SimEvent.DeadlineApproaching && (it as SimEvent.DeadlineApproaching).ticksRemaining == 5 })
    }

    @Test
    fun `DeadlineApproaching does NOT fire at 19 ticks before dueTick`() {
        val world = baseWorld(currentDay = 81, dueTick = 100)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineApproaching })
    }

    @Test
    fun `DeadlineApproaching does NOT fire at 11 ticks before dueTick`() {
        val world = baseWorld(currentDay = 89, dueTick = 100)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineApproaching })
    }

    @Test
    fun `DeadlineApproaching does NOT fire at 6 ticks before dueTick`() {
        val world = baseWorld(currentDay = 94, dueTick = 100)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineApproaching })
    }

    @Test
    fun `DeadlineApproaching does NOT fire for MISSED deadlines`() {
        val world = baseWorld(currentDay = 80, dueTick = 100, deadlineStatus = DeadlineStatus.MISSED)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineApproaching })
    }

    @Test
    fun `DeadlineApproaching does NOT fire for MET deadlines`() {
        val world = baseWorld(currentDay = 80, dueTick = 100, deadlineStatus = DeadlineStatus.MET)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineApproaching })
    }

    // --- DeadlineMissed emission ---

    @Test
    fun `DeadlineMissed fires the tick after dueTick`() {
        val world = baseWorld(currentDay = 101, dueTick = 100)
        val events = generateEvents(world)
        assertTrue(events.any { it is SimEvent.DeadlineMissed })
    }

    @Test
    fun `DeadlineMissed does NOT fire before dueTick`() {
        val world = baseWorld(currentDay = 100, dueTick = 100)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineMissed })
    }

    @Test
    fun `DeadlineMissed does NOT fire again if status already MISSED`() {
        val world = baseWorld(currentDay = 105, dueTick = 100, deadlineStatus = DeadlineStatus.MISSED)
        val events = generateEvents(world)
        assertFalse(events.any { it is SimEvent.DeadlineMissed })
    }

    @Test
    fun `SimEngine sets status to MISSED immediately after DeadlineMissed fires`() {
        val engine = SimEngine()
        // Start at tick 100 (dueTick), advance to 101 so the miss fires.
        val world = baseWorld(currentDay = 100, dueTick = 100)
        val result = engine.tick(world)
        assertEquals(DeadlineStatus.MISSED, result.world.deadlines[deadlineId]!!.status)
    }

    // --- ExtendDeadline applicator ---

    @Test
    fun `ExtendDeadline pushes dueTick forward by 10 and sets status EXTENDED`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.ExtendDeadline(deadlineId, artistId))))
        val d = updated.deadlines[deadlineId]!!
        assertEquals(110, d.dueTick)
        assertEquals(DeadlineStatus.EXTENDED, d.status)
    }

    @Test
    fun `ExtendDeadline applies loyalty penalty on first extension`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.ExtendDeadline(deadlineId, artistId))))
        val artist = updated.artists[artistId]!!
        assertEquals(0.5f - 0.10f, artist.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `ExtendDeadline on already-EXTENDED deadline does not extend further`() {
        val world = baseWorld(currentDay = 90, dueTick = 110, deadlineStatus = DeadlineStatus.EXTENDED)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.ExtendDeadline(deadlineId, artistId))))
        val d = updated.deadlines[deadlineId]!!
        assertEquals(110, d.dueTick)  // unchanged
        assertEquals(DeadlineStatus.EXTENDED, d.status)
    }

    @Test
    fun `ExtendDeadline on already-EXTENDED deadline applies reduced loyalty penalty`() {
        val world = baseWorld(currentDay = 90, dueTick = 110, deadlineStatus = DeadlineStatus.EXTENDED)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.ExtendDeadline(deadlineId, artistId))))
        val artist = updated.artists[artistId]!!
        assertEquals(0.5f - 0.05f, artist.dimensions.loyalty, 0.001f)
    }

    // --- MeetDeadline applicator ---

    @Test
    fun `MeetDeadline sets deadline status to MET`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.MeetDeadline(deadlineId, artistId))))
        assertEquals(DeadlineStatus.MET, updated.deadlines[deadlineId]!!.status)
    }

    @Test
    fun `MeetDeadline increases artist loyalty by 0_05`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.MeetDeadline(deadlineId, artistId))))
        assertEquals(0.5f + 0.05f, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    @Test
    fun `MeetDeadline increases PRESS reputation by 0_02`() {
        val world = baseWorld(currentDay = 90, dueTick = 100)
        val pressRepBefore = world.label.reputation[ReputationCommunity.PRESS]!!
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.MeetDeadline(deadlineId, artistId))))
        assertEquals(pressRepBefore + 0.02f, updated.label.reputation[ReputationCommunity.PRESS]!!, 0.001f)
    }

    @Test
    fun `MeetDeadline is a no-op if deadline already MET`() {
        val world = baseWorld(currentDay = 90, dueTick = 100, deadlineStatus = DeadlineStatus.MET)
        val (updated, _) = applyResponse(world, option(listOf(StateEffect.MeetDeadline(deadlineId, artistId))))
        // World is unchanged
        assertEquals(world.deadlines[deadlineId]!!.status, updated.deadlines[deadlineId]!!.status)
        assertEquals(world.artists[artistId]!!.dimensions.loyalty, updated.artists[artistId]!!.dimensions.loyalty, 0.001f)
    }

    // --- SeasonEnded emission ---

    @Test
    fun `SeasonEnded fires when currentDay reaches seasonEndTick`() {
        val engine = SimEngine()
        val world = baseWorld(currentDay = 179, dueTick = 200).copy(season = SeasonState(1, 0, 180))
        val result = engine.tick(world)
        assertTrue(result.events.any { it is SimEvent.SeasonEnded })
    }

    @Test
    fun `SeasonEnded does not fire again once seasonEndedEmitted is true`() {
        val engine = SimEngine()
        val world = baseWorld(currentDay = 180, dueTick = 200).copy(
            season = SeasonState(1, 0, 180),
            seasonEndedEmitted = true
        )
        val result = engine.tick(world)
        assertFalse(result.events.any { it is SimEvent.SeasonEnded })
    }

    @Test
    fun `SeasonEnded sets seasonEndedEmitted flag on world`() {
        val engine = SimEngine()
        val world = baseWorld(currentDay = 179, dueTick = 200).copy(season = SeasonState(1, 0, 180))
        val result = engine.tick(world)
        assertTrue(result.world.seasonEndedEmitted)
    }

    @Test
    fun `SeasonEnded carries correct seasonNumber`() {
        val engine = SimEngine()
        val world = baseWorld(currentDay = 179, dueTick = 200).copy(season = SeasonState(seasonNumber = 2, seasonStartTick = 0, seasonEndTick = 180))
        val result = engine.tick(world)
        val event = result.events.filterIsInstance<SimEvent.SeasonEnded>().firstOrNull()
        assertEquals(2, event?.seasonNumber)
    }
}
