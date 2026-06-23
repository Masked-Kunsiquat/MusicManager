package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.GenreAction
import com.github.maskedkunisquat.musicmanager.logic.model.LabelAesthetic
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import kotlin.math.ln

object LabelIdentityEvaluator {

    fun evaluate(actions: List<GenreAction>, artists: Collection<ArtistState>): LabelIdentity {
        // Fold player actions into per-genre weights starting from a 0.5 neutral baseline.
        // Weights clamp to [0, 1]; a genre only appears in the map if it was touched.
        val weights = mutableMapOf<String, Float>()
        for (action in actions) {
            val current = weights.getOrDefault(action.genre, 0.5f)
            weights[action.genre] = (current + action.delta).coerceIn(0f, 1f)
        }
        val primaryGenre = weights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value > 0f }?.key
        return LabelIdentity(
            genreWeights = weights.toMap(),
            primaryGenre = primaryGenre,
            focusScore = computeFocusScore(weights),
            aesthetic = deriveAesthetic(artists)
        )
    }

    // Normalized inverse Shannon entropy over the weight distribution.
    // 0 = weights are evenly spread (no focus), 1 = one genre holds all weight.
    // Empty map → 0f (no identity established yet).
    internal fun computeFocusScore(weights: Map<String, Float>): Float {
        if (weights.isEmpty()) return 0f
        val total = weights.values.sum()
        if (total <= 0f) return 0f
        val entropy = weights.values.fold(0.0) { acc, w ->
            val p = (w / total).toDouble()
            if (p <= 0.0) acc else acc - p * ln(p)
        }.toFloat()
        val maxEntropy = ln(weights.size.toDouble()).toFloat()
        return if (maxEntropy <= 0f) 1f else (1f - entropy / maxEntropy).coerceIn(0f, 1f)
    }

    // Aesthetic is driven by roster composition, not genre weights.
    // Priority: EXPERIMENTAL > MAINSTREAM > UNDERGROUND > ECLECTIC.
    internal fun deriveAesthetic(artists: Collection<ArtistState>): LabelAesthetic {
        if (artists.isEmpty()) return LabelAesthetic.ECLECTIC
        val n = artists.size
        val highVolatility = artists.count { it.dimensions.volatility >= 0.65f }
        if (highVolatility * 2 > n) return LabelAesthetic.EXPERIMENTAL
        val highCommercial = artists.count { it.dimensions.commercialAppetite >= 0.65f }
        if (highCommercial * 2 > n) return LabelAesthetic.MAINSTREAM
        val topGenreCount = artists.groupingBy { it.genre }.eachCount().values.maxOrNull() ?: 0
        if (topGenreCount.toFloat() / n > 0.70f) return LabelAesthetic.UNDERGROUND
        return LabelAesthetic.ECLECTIC
    }
}
