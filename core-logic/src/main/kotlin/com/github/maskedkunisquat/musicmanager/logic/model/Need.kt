package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
enum class NeedType {
    CREATIVE_FULFILLMENT,
    FINANCIAL_SECURITY,
    RECOGNITION,
    BELONGING,
    AUTONOMY
}

@Serializable
data class NeedState(
    val type: NeedType,
    val value: Float,       // 0f..1f; 1f = fully satisfied
    val decayRate: Float    // units lost per tick, before volatility scaling
) {
    init {
        require(value in 0f..1f) { "value must be in 0f..1f, was $value" }
        require(decayRate in 0f..1f) { "decayRate must be in 0f..1f, was $decayRate" }
    }
}
