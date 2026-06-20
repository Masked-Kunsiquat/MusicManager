package com.github.maskedkunisquat.musicmanager.logic

import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.sim.tickMarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MarketTickerTest {

    private fun market(vararg pairs: Pair<String, Float>) = MarketState(mapOf(*pairs))

    @Test
    fun `trends stay within 0f to 1f after single tick`() {
        val market = market("pop" to 0.0f, "indie" to 1.0f, "folk" to 0.5f)
        val result = tickMarket(market, Random(42))
        result.genreTrends.values.forEach { trend ->
            assertTrue("trend $trend out of [0,1]", trend in 0f..1f)
        }
    }

    @Test
    fun `same seed produces same result (determinism)`() {
        val market = market("pop" to 0.5f, "hip-hop" to 0.6f)
        val a = tickMarket(market, Random(99))
        val b = tickMarket(market, Random(99))
        assertEquals(a, b)
    }

    @Test
    fun `different seeds produce different results`() {
        val market = market("pop" to 0.5f)
        val a = tickMarket(market, Random(1))
        val b = tickMarket(market, Random(2))
        // Extremely unlikely to be equal given float arithmetic with different RNG seeds
        assertTrue(a != b)
    }

    @Test
    fun `all genre trends stay in bounds after 200 ticks`() {
        var market = market("pop" to 0.5f, "indie" to 0.9f, "folk" to 0.1f, "hip-hop" to 0.5f)
        repeat(200) { tick ->
            market = tickMarket(market, Random(seed = 42L xor tick.toLong()))
        }
        market.genreTrends.values.forEach { trend ->
            assertTrue("trend $trend drifted out of [0,1] after 200 ticks", trend in 0f..1f)
        }
    }

    @Test
    fun `mean-reversion pulls extreme trends toward 0_5f over time`() {
        // Start a genre pegged at an extreme; after many ticks it should drift inward.
        var market = market("pop" to 0.95f)
        repeat(100) { tick ->
            market = tickMarket(market, Random(seed = 7L xor tick.toLong()))
        }
        val finalTrend = market.genreTrends["pop"]!!
        assertTrue(
            "Expected trend to move toward 0.5 from 0.95, got $finalTrend",
            finalTrend < 0.95f
        )
    }

    @Test
    fun `high trend decays faster than moderate trend (self-balancing)`() {
        val highMarket = market("pop" to 0.85f)
        val midMarket = market("pop" to 0.65f)

        // Run both with the same RNG sequence so randomness is controlled.
        val highAfter = tickMarket(highMarket, Random(42))
        val midAfter = tickMarket(midMarket, Random(42))

        val highDelta = highMarket.genreTrends["pop"]!! - highAfter.genreTrends["pop"]!!
        val midDelta = midMarket.genreTrends["pop"]!! - midAfter.genreTrends["pop"]!!

        // The high trend should have a stronger corrective pull downward.
        // Both deltas may be positive or negative depending on random component,
        // but highDelta should be larger (or equal) because of the accelerant.
        assertTrue(
            "High trend ($highDelta drop) should have stronger correction than mid ($midDelta drop)",
            highDelta >= midDelta - 0.001f  // slight tolerance for float comparison
        )
    }
}
