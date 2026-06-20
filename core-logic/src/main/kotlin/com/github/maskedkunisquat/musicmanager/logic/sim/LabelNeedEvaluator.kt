package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

object LabelNeedEvaluator {

    // Funds are stored in cents.
    private val CASH_FLOW_BUCKETS = listOf(
        500_000L   to 0.10f,   // < $5k
        2_000_000L to 0.40f,   // < $20k
        5_000_000L to 0.65f    // < $50k
    )

    fun evaluate(world: SimWorld): Map<LabelNeedType, Float> = mapOf(
        LabelNeedType.CASH_FLOW to cashFlow(world),
        LabelNeedType.GENRE_DIVERSITY to genreDiversity(world)
    )

    fun cashFlow(world: SimWorld): Float {
        val funds = world.label.funds
        for ((threshold, value) in CASH_FLOW_BUCKETS) {
            if (funds < threshold) return value
        }
        return 1.0f
    }

    fun genreDiversity(world: SimWorld): Float {
        val artists = world.artists.values
        if (artists.isEmpty()) return 1.0f
        val distinctGenres = artists.map { it.genre }.toSet().size
        val denominator = maxOf(4, artists.size)
        return distinctGenres.toFloat() / denominator
    }
}
