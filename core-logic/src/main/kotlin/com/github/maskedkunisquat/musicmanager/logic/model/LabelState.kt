package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelState(
    val funds: Long,                                    // in cents
    val reputation: Map<ReputationCommunity, Float>,    // 0f..1f per community
    val rosterIds: Set<String>,
    val capabilities: Set<CapabilityType> = emptySet(),
    // genre -> preference weight (0-1); updated by PursueLead (+0.05) and PassLead (-0.03).
    val tasteVector: Map<String, Float> = emptyMap()
)

@Serializable
enum class ReputationCommunity {
    INDIE_SCENE,
    COMMERCIAL,
    PRESS,
    VENUE_BOOKERS
}
