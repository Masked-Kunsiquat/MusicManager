package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelState(
    val funds: Long,                                    // in cents
    val reputation: Map<ReputationCommunity, Float>,    // 0f..1f per community
    val rosterIds: Set<String>,
    val capabilities: Set<CapabilityType> = emptySet()
)

@Serializable
enum class ReputationCommunity {
    INDIE_SCENE,
    COMMERCIAL,
    PRESS,
    VENUE_BOOKERS
}
