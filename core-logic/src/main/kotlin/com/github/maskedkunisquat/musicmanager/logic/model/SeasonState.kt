package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonState(
    val seasonNumber: Int = 1,
    val seasonStartTick: Int = 0,
    val seasonEndTick: Int = 180,  // 180 ticks ≈ 20 real days (same as contract length)
    // Snapshot at season start — used by SeasonSummaryEvaluator for delta computation.
    // Set by WorldInitializer (season 1) and NewSeasonInitializer (season 2+).
    val startFunds: Long = 0L,
    val startReputation: Map<String, Float> = emptyMap()  // keyed by ReputationCommunity.name
)
