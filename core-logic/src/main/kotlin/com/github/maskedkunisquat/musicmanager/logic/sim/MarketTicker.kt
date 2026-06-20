package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import kotlin.random.Random

// Per-tick drift applied to each genre trend. Chosen so a trend starting at 0.5f
// takes ~25 ticks (≈4 real days at 160 min/tick) to reach an extreme against
// mean-reversion — slow enough to feel macro, fast enough to matter in a 20-day arc.
private const val BASE_DRIFT = 0.04f
private const val REVERSION_STRENGTH = 0.02f  // fraction of distance pulled toward 0.5f per tick
private const val EXTREME_THRESHOLD_HIGH = 0.7f
private const val EXTREME_THRESHOLD_LOW = 0.3f

internal fun tickMarket(market: MarketState, rng: Random): MarketState {
    val updated = market.genreTrends.mapValues { (_, trend) ->
        val randomDrift = (rng.nextFloat() - 0.5f) * BASE_DRIFT * 2f
        val reversion = (0.5f - trend) * REVERSION_STRENGTH
        // Self-balancing: double the corrective push at extremes so trends don't pin at 0/1.
        val accelerant = when {
            trend > EXTREME_THRESHOLD_HIGH -> -BASE_DRIFT
            trend < EXTREME_THRESHOLD_LOW -> +BASE_DRIFT
            else -> 0f
        }
        (trend + randomDrift + reversion + accelerant).coerceIn(0f, 1f)
    }
    return market.copy(genreTrends = updated)
}
