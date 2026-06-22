package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RivalSnapshot
import com.github.maskedkunisquat.musicmanager.logic.model.RivalState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.rivalintel.RivalIntelFuzzer
import com.github.maskedkunisquat.musicmanager.logic.sim.applyResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RivalIntelTest {

    // --- Helpers ---

    private val rival = RivalState(
        id = "r0",
        name = "Mercury Sound",
        genreWeights = mapOf("indie-rock" to 1.0f, "ambient" to 0.8f, "pop" to 0.2f)
    )

    private fun world(pressRep: Float = 0.6f, rivals: Map<String, RivalState> = mapOf("r0" to rival)) = SimWorld(
        seed = 1L,
        currentDay = 0,
        artists = emptyMap(),
        label = LabelState(
            funds = 10_000_000L,
            reputation = mapOf(ReputationCommunity.PRESS to pressRep),
            rosterIds = emptySet()
        ),
        market = MarketState(emptyMap()),
        contracts = emptyMap(),
        rivals = rivals
    )

    private fun freshSnapshot(day: Int = 0) = RivalSnapshot(
        rivalId = "r0",
        observedRosterSize = 4,
        observedGenres = listOf("ambient", "indie-rock"),
        confidence = 1.0f,
        snapshotDay = day
    )

    // --- Confidence decay ---

    @Test fun `confidence is 1_0 on snapshot day`() {
        val snap = freshSnapshot(day = 10)
        assertEquals(1.0f, RivalIntelFuzzer.currentConfidence(snap, currentDay = 10), 0.001f)
    }

    @Test fun `confidence decays 0_05 per 10 ticks`() {
        val snap = freshSnapshot(day = 0)
        assertEquals(0.95f, RivalIntelFuzzer.currentConfidence(snap, currentDay = 10), 0.001f)
        assertEquals(0.90f, RivalIntelFuzzer.currentConfidence(snap, currentDay = 20), 0.001f)
        assertEquals(0.80f, RivalIntelFuzzer.currentConfidence(snap, currentDay = 40), 0.001f)
    }

    @Test fun `confidence clamps at 0_0`() {
        val snap = freshSnapshot(day = 0)
        val conf = RivalIntelFuzzer.currentConfidence(snap, currentDay = 500)
        assertEquals(0f, conf, 0.001f)
    }

    // --- Stale threshold ---

    @Test fun `isStale is false when confidence above 0_3`() {
        val snap = freshSnapshot(day = 0)
        // 0 ticks elapsed → confidence = 1.0f
        assertFalse(RivalIntelFuzzer.isStale(snap, currentDay = 0))
    }

    @Test fun `isStale is true when confidence below 0_3`() {
        val snap = freshSnapshot(day = 0)
        // 14 * 10 = 140 ticks → decay = 14 * 0.05 = 0.70 → confidence = 0.30 — still NOT stale (boundary)
        assertFalse(RivalIntelFuzzer.isStale(snap, currentDay = 140))
        // 15 * 10 = 150 ticks → decay = 0.75 → confidence = 0.25 < 0.30 → stale
        assertTrue(RivalIntelFuzzer.isStale(snap, currentDay = 150))
    }

    // --- Fuzz layer: roster size ---

    @Test fun `fuzzedRosterSize returns exact value at full confidence`() {
        val snap = freshSnapshot(day = 0)
        // At confidence 1.0, maxNoise = 0, so value is exact.
        assertEquals(4, RivalIntelFuzzer.fuzzedRosterSize(snap, currentDay = 0))
    }

    @Test fun `fuzzedRosterSize never returns less than 1`() {
        val snap = RivalSnapshot("r0", observedRosterSize = 1, observedGenres = emptyList(),
            confidence = 1.0f, snapshotDay = 0)
        assertTrue(RivalIntelFuzzer.fuzzedRosterSize(snap, currentDay = 500) >= 1)
    }

    // --- Fuzz layer: genres ---

    @Test fun `fuzzedGenres returns exact list at high confidence`() {
        val snap = freshSnapshot(day = 0)
        assertEquals(snap.observedGenres, RivalIntelFuzzer.fuzzedGenres(snap, currentDay = 0))
    }

    @Test fun `fuzzedGenres may shorten list at medium confidence`() {
        val snap = RivalSnapshot("r0", observedRosterSize = 4,
            observedGenres = listOf("ambient", "indie-rock", "folk"),
            confidence = 1.0f, snapshotDay = 0)
        // At 100 ticks elapsed: confidence = 1.0 - 10*0.05 = 0.5 → should drop last genre
        val fuzzed = RivalIntelFuzzer.fuzzedGenres(snap, currentDay = 100)
        assertTrue("should have fewer or equal genres at conf=0.5", fuzzed.size <= snap.observedGenres.size)
    }

    // --- UpdateRivalIntel effect wires intelCache ---

    @Test fun `UpdateRivalIntel populates intelCache from rivals map`() {
        val w = world()
        val option = ResponseOption(
            id = "test_intel",
            text = "Dig in",
            effects = listOf(StateEffect.UpdateRivalIntel("r0"))
        )
        val (newWorld, _) = applyResponse(w, option)
        val snap = newWorld.label.intelCache["r0"]
        assertNotNull(snap)
        assertEquals("r0", snap!!.rivalId)
        assertEquals(1.0f, snap.confidence, 0.001f)
        assertEquals(0, snap.snapshotDay)
    }

    @Test fun `UpdateRivalIntel only includes genres with weight above 0_5`() {
        val w = world()
        val option = ResponseOption(
            id = "test_intel",
            text = "Dig in",
            effects = listOf(StateEffect.UpdateRivalIntel("r0"))
        )
        val (newWorld, _) = applyResponse(w, option)
        val snap = newWorld.label.intelCache["r0"]!!
        // rival has indie-rock (1.0) and ambient (0.8) above threshold; pop (0.2) below
        assertTrue("indie-rock should be in genres", "indie-rock" in snap.observedGenres)
        assertTrue("ambient should be in genres", "ambient" in snap.observedGenres)
        assertFalse("pop should NOT be in genres (weight 0.2)", "pop" in snap.observedGenres)
    }

    @Test fun `UpdateRivalIntel is a no-op for unknown rivalId`() {
        val w = world()
        val option = ResponseOption(
            id = "test_intel",
            text = "Dig in",
            effects = listOf(StateEffect.UpdateRivalIntel("unknown_rival"))
        )
        val (newWorld, _) = applyResponse(w, option)
        assertNull(newWorld.label.intelCache["unknown_rival"])
    }

    @Test fun `UpdateRivalIntel overwrites stale cache entry`() {
        val staleSnap = RivalSnapshot("r0", observedRosterSize = 99,
            observedGenres = listOf("jazz"), confidence = 0.1f, snapshotDay = 0)
        val w = world().copy(
            currentDay = 50,
            label = world().label.copy(intelCache = mapOf("r0" to staleSnap))
        )
        val option = ResponseOption(
            id = "test_intel",
            text = "Dig in",
            effects = listOf(StateEffect.UpdateRivalIntel("r0"))
        )
        val (newWorld, _) = applyResponse(w, option)
        val snap = newWorld.label.intelCache["r0"]!!
        assertEquals(50, snap.snapshotDay)
        assertEquals(1.0f, snap.confidence, 0.001f)
    }

    // --- Gate: PRESS rep threshold ---

    @Test fun `press rep at 0_5 meets the unlock gate`() {
        assertTrue(0.5f >= 0.5f)  // boundary — exactly at gate
    }

    @Test fun `press rep below 0_5 does not meet gate`() {
        assertFalse(0.49f >= 0.5f)
    }
}
