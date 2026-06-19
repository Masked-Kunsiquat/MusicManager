package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistDimensions(
    val confidence: Float,
    val commercialAppetite: Float,
    val volatility: Float,
    val loyalty: Float
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in 0f..1f, was $confidence" }
        require(commercialAppetite in 0f..1f) { "commercialAppetite must be in 0f..1f, was $commercialAppetite" }
        require(volatility in 0f..1f) { "volatility must be in 0f..1f, was $volatility" }
        require(loyalty in 0f..1f) { "loyalty must be in 0f..1f, was $loyalty" }
    }
}
