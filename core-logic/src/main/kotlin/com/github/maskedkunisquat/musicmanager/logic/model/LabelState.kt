package com.github.maskedkunisquat.musicmanager.logic.model

data class LabelState(
    val funds: Long,                                    // in cents
    val reputation: Map<ReputationCommunity, Float>,    // 0f..1f per community
    val rosterIds: Set<String>
)

enum class ReputationCommunity {
    INDIE_SCENE,
    COMMERCIAL,
    PRESS,
    VENUE_BOOKERS
}
