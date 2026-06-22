package com.github.maskedkunisquat.musicmanager.logic.model

// Entity-layer counts extracted by SimRepositoryImpl and passed to SeasonSummaryEvaluator.
// Keeps entity parsing (core-data) separate from pure summary arithmetic (core-logic).
data class SeasonFacts(
    val rivalPoachArtistIds: Set<String>,
    val renewalWalkedArtistIds: Set<String>,
    val deadlinesMet: Int,
    val deadlinesMissed: Int
)
