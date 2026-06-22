package com.github.maskedkunisquat.musicmanager.logic.model

data class SeasonSummary(
    val seasonNumber: Int,
    val artistsRetained: Int,
    val artistsLost: Int,
    val deadlinesMet: Int,
    val deadlinesMissed: Int,
    val fundsNet: Long,                              // cents; positive = profit, negative = loss
    val reputationDeltas: Map<String, Float>         // keyed by ReputationCommunity.name
)
