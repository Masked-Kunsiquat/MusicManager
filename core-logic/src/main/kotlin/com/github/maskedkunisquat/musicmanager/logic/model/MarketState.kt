package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketState(
    val genreTrends: Map<String, Float>     // genre slug -> trend strength 0f..1f
)
