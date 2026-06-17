package com.github.maskedkunisquat.musicmanager.logic.model

enum class NeedType {
    CREATIVE_FULFILLMENT,
    FINANCIAL_SECURITY,
    RECOGNITION,
    BELONGING,
    AUTONOMY
}

data class NeedState(
    val type: NeedType,
    val value: Float,       // 0f..1f; 1f = fully satisfied
    val decayRate: Float    // units lost per tick, before volatility scaling
)
