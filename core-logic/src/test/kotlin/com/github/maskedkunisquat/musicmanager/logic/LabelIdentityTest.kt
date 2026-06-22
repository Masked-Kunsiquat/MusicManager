package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.GenreAction
import com.github.maskedkunisquat.musicmanager.logic.model.LabelAesthetic
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.DemoState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ScoutState
import com.github.maskedkunisquat.musicmanager.logic.sim.LabelIdentityEvaluator
import com.github.maskedkunisquat.musicmanager.logic.sim.tickScouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LabelIdentityTest {

    private fun baseArtist(
        id: String = "a",
        genre: String = "indie-rock",
        volatility: Float = 0.5f,
        commercialAppetite: Float = 0.5f
    ) = ArtistState(
        id = id,
        name = "Test Artist",
        genre = genre,
        dimensions = ArtistDimensions(
            confidence = 0.5f,
            commercialAppetite = commercialAppetite,
            volatility = volatility,
            loyalty = 0.5f
        ),
        needs = mapOf(NeedType.CREATIVE_FULFILLMENT to NeedState(NeedType.CREATIVE_FULFILLMENT, 0.7f, 0.02f)),
        activeWants = emptyList(),
        contractId = null
    )

    private fun prospect(id: String, genre: String) = ProspectState(
        id = id,
        name = "Test Prospect",
        genre = genre,
        dimensions = ArtistDimensions(0.5f, 0.5f, 0.5f, 0.5f),
        signabilityScore = 0.5f,
        demo = DemoState("demo", 0.5f, 0)
    )

    private fun scout(id: String, focusGenres: Set<String> = emptySet()) = ScoutState(
        id = id,
        name = "Scout",
        focusGenres = focusGenres,
        lastReportDay = 0
    )

    // --- LabelIdentityEvaluator: genreWeights ---

    @Test
    fun `empty actions produce empty genreWeights and null primaryGenre`() {
        val identity = LabelIdentityEvaluator.evaluate(emptyList(), emptyList())
        assertTrue(identity.genreWeights.isEmpty())
        assertNull(identity.primaryGenre)
    }

    @Test
    fun `PursueLead action adds 0_05 to genre weight starting from 0_5 neutral`() {
        val identity = LabelIdentityEvaluator.evaluate(
            listOf(GenreAction("indie-rock", +0.05f)),
            emptyList()
        )
        assertEquals(0.55f, identity.genreWeights["indie-rock"]!!, 0.001f)
    }

    @Test
    fun `PassLead action subtracts 0_03 from genre weight`() {
        val identity = LabelIdentityEvaluator.evaluate(
            listOf(GenreAction("pop", -0.03f)),
            emptyList()
        )
        assertEquals(0.47f, identity.genreWeights["pop"]!!, 0.001f)
    }

    @Test
    fun `SignArtist action adds 0_10 to genre weight`() {
        val identity = LabelIdentityEvaluator.evaluate(
            listOf(GenreAction("hip-hop", +0.10f)),
            emptyList()
        )
        assertEquals(0.60f, identity.genreWeights["hip-hop"]!!, 0.001f)
    }

    @Test
    fun `multiple actions on same genre accumulate correctly`() {
        val actions = listOf(
            GenreAction("indie-rock", +0.05f),
            GenreAction("indie-rock", +0.05f),
            GenreAction("indie-rock", +0.10f)
        )
        val identity = LabelIdentityEvaluator.evaluate(actions, emptyList())
        assertEquals(0.70f, identity.genreWeights["indie-rock"]!!, 0.001f)
    }

    @Test
    fun `genre weight clamps at 1_0 on overflow`() {
        val actions = List(10) { GenreAction("indie-rock", +0.10f) }
        val identity = LabelIdentityEvaluator.evaluate(actions, emptyList())
        assertEquals(1.0f, identity.genreWeights["indie-rock"]!!, 0.001f)
    }

    @Test
    fun `genre weight clamps at 0_0 on underflow`() {
        val actions = List(10) { GenreAction("indie-rock", -0.10f) }
        val identity = LabelIdentityEvaluator.evaluate(actions, emptyList())
        assertEquals(0.0f, identity.genreWeights["indie-rock"]!!, 0.001f)
    }

    @Test
    fun `primaryGenre is the highest-weight genre`() {
        val actions = listOf(
            GenreAction("indie-rock", +0.10f),
            GenreAction("pop", +0.05f)
        )
        val identity = LabelIdentityEvaluator.evaluate(actions, emptyList())
        assertEquals("indie-rock", identity.primaryGenre)
    }

    // --- LabelIdentityEvaluator: focusScore ---

    @Test
    fun `focusScore is 0 for empty weights`() {
        assertEquals(0f, LabelIdentityEvaluator.computeFocusScore(emptyMap()), 0.001f)
    }

    @Test
    fun `focusScore is 1 for a single genre`() {
        val score = LabelIdentityEvaluator.computeFocusScore(mapOf("indie-rock" to 0.8f))
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `focusScore is near 0 for uniformly weighted genres`() {
        // Four genres with identical weights → maximum entropy → focusScore near 0.
        val weights = mapOf("a" to 0.5f, "b" to 0.5f, "c" to 0.5f, "d" to 0.5f)
        val score = LabelIdentityEvaluator.computeFocusScore(weights)
        assertTrue("Expected focusScore < 0.1 for even distribution, got $score", score < 0.1f)
    }

    @Test
    fun `focusScore increases when one genre dominates`() {
        val even = mapOf("a" to 0.5f, "b" to 0.5f, "c" to 0.5f)
        val skewed = mapOf("a" to 0.9f, "b" to 0.2f, "c" to 0.1f)
        val evenScore = LabelIdentityEvaluator.computeFocusScore(even)
        val skewedScore = LabelIdentityEvaluator.computeFocusScore(skewed)
        assertTrue("Skewed score $skewedScore should be > even score $evenScore", skewedScore > evenScore)
    }

    // --- LabelIdentityEvaluator: aesthetic ---

    @Test
    fun `EXPERIMENTAL when majority volatility is at least 0_65`() {
        val artists = listOf(
            baseArtist("a", volatility = 0.70f),
            baseArtist("b", volatility = 0.80f),
            baseArtist("c", volatility = 0.30f)  // minority
        )
        assertEquals(LabelAesthetic.EXPERIMENTAL, LabelIdentityEvaluator.deriveAesthetic(artists))
    }

    @Test
    fun `MAINSTREAM when majority commercialAppetite is at least 0_65`() {
        val artists = listOf(
            baseArtist("a", volatility = 0.40f, commercialAppetite = 0.80f),
            baseArtist("b", volatility = 0.40f, commercialAppetite = 0.70f),
            baseArtist("c", volatility = 0.40f, commercialAppetite = 0.20f)
        )
        assertEquals(LabelAesthetic.MAINSTREAM, LabelIdentityEvaluator.deriveAesthetic(artists))
    }

    @Test
    fun `UNDERGROUND when single genre exceeds 70 percent of roster`() {
        val artists = listOf(
            baseArtist("a", genre = "indie-rock"),
            baseArtist("b", genre = "indie-rock"),
            baseArtist("c", genre = "indie-rock"),
            baseArtist("d", genre = "pop")  // 25% minority
        )
        assertEquals(LabelAesthetic.UNDERGROUND, LabelIdentityEvaluator.deriveAesthetic(artists))
    }

    @Test
    fun `ECLECTIC when no condition is met`() {
        val artists = listOf(
            baseArtist("a", genre = "indie-rock", volatility = 0.4f),
            baseArtist("b", genre = "pop", volatility = 0.4f),
            baseArtist("c", genre = "hip-hop", volatility = 0.4f)
        )
        assertEquals(LabelAesthetic.ECLECTIC, LabelIdentityEvaluator.deriveAesthetic(artists))
    }

    @Test
    fun `ECLECTIC for empty roster`() {
        assertEquals(LabelAesthetic.ECLECTIC, LabelIdentityEvaluator.deriveAesthetic(emptyList()))
    }

    // --- ScoutTicker identity weighting ---

    @Test
    fun `scouts bias toward primaryGenre when focusScore exceeds 0_6`() {
        val focusedIdentity = LabelIdentity(
            genreWeights = mapOf("indie-rock" to 0.9f),
            primaryGenre = "indie-rock",
            focusScore = 0.95f,
            aesthetic = LabelAesthetic.UNDERGROUND
        )
        val scouts = mapOf("scout_0" to scout("scout_0", focusGenres = setOf("pop")))
        val prospects = mapOf(
            "p_indie_1" to prospect("p_indie_1", "indie-rock"),
            "p_indie_2" to prospect("p_indie_2", "indie-rock"),
            "p_indie_3" to prospect("p_indie_3", "indie-rock"),
            "p_pop_1"   to prospect("p_pop_1",   "pop")
        )
        // Run 20 scout ticks with a fixed seed and count genre of reported prospects.
        var indieReports = 0
        var otherReports = 0
        repeat(20) { day ->
            // Stagger lastReportDay so the scout reports each time.
            val staggeredScouts = scouts.mapValues { (_, s) -> s.copy(lastReportDay = day * 10) }
            val (_, events) = tickScouts(staggeredScouts, day * 10 + 8, prospects, Random(day.toLong()), focusedIdentity)
            for (e in events.filterIsInstance<SimEvent.ScoutReport>()) {
                val genre = prospects[e.prospectId]?.genre
                if (genre == "indie-rock") indieReports++ else otherReports++
            }
        }
        assertTrue("Expected mostly indie-rock reports (got $indieReports indie, $otherReports other)",
            indieReports > otherReports)
    }

    @Test
    fun `scouts use existing focus logic when identity is null`() {
        val scouts = mapOf("scout_0" to scout("scout_0", focusGenres = setOf("pop")))
        val prospects = mapOf(
            "p_pop_1"   to prospect("p_pop_1",   "pop"),
            "p_pop_2"   to prospect("p_pop_2",   "pop"),
            "p_indie_1" to prospect("p_indie_1", "indie-rock")
        )
        var popReports = 0
        var otherReports = 0
        repeat(20) { day ->
            val staggeredScouts = scouts.mapValues { (_, s) -> s.copy(lastReportDay = day * 10) }
            val (_, events) = tickScouts(staggeredScouts, day * 10 + 8, prospects, Random(day.toLong()), null)
            for (e in events.filterIsInstance<SimEvent.ScoutReport>()) {
                val genre = prospects[e.prospectId]?.genre
                if (genre == "pop") popReports++ else otherReports++
            }
        }
        // Scout focuses on pop, so pop should dominate even without identity.
        assertTrue("Expected mostly pop reports (got $popReports pop, $otherReports other)",
            popReports > otherReports)
    }

    @Test
    fun `scouts do not bias when focusScore is at or below threshold`() {
        val weakIdentity = LabelIdentity(
            genreWeights = mapOf("indie-rock" to 0.55f, "pop" to 0.52f),
            primaryGenre = "indie-rock",
            focusScore = 0.05f,   // below 0.6 threshold
            aesthetic = LabelAesthetic.ECLECTIC
        )
        val scouts = mapOf("scout_0" to scout("scout_0", focusGenres = setOf("pop")))
        val prospects = mapOf(
            "p_indie_1" to prospect("p_indie_1", "indie-rock"),
            "p_pop_1"   to prospect("p_pop_1",   "pop"),
            "p_pop_2"   to prospect("p_pop_2",   "pop")
        )
        // With weak identity, the scout's own focus genre (pop) should still dominate.
        var popReports = 0
        var indieReports = 0
        repeat(20) { day ->
            val staggeredScouts = scouts.mapValues { (_, s) -> s.copy(lastReportDay = day * 10) }
            val (_, events) = tickScouts(staggeredScouts, day * 10 + 8, prospects, Random(day.toLong()), weakIdentity)
            for (e in events.filterIsInstance<SimEvent.ScoutReport>()) {
                val genre = prospects[e.prospectId]?.genre
                if (genre == "pop") popReports++ else indieReports++
            }
        }
        assertTrue("Expected scout focus (pop) to dominate with weak identity (pop=$popReports, indie=$indieReports)",
            popReports >= indieReports)
    }
}
