package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelState(
    val funds: Long,                                    // in cents
    val reputation: Map<ReputationCommunity, Float>,    // 0f..1f per community
    val rosterIds: Set<String>,
    val capabilities: Set<CapabilityType> = emptySet(),
    // genre -> preference weight (0-1); updated by PursueLead (+0.05) and PassLead (-0.03).
    val tasteVector: Map<String, Float> = emptyMap(),
    // rivalId -> last observed intel snapshot; populated by UpdateRivalIntel effect.
    val intelCache: Map<String, RivalSnapshot> = emptyMap()
)

/**
 * A point-in-time observation of a rival label, captured when the player chooses to
 * "dig into" a RivalSigning or RivalPoach event. Fuzz is applied at display time
 * (not stored here) based on how stale the snapshot is.
 */
@Serializable
data class RivalSnapshot(
    val rivalId: String,
    val observedRosterSize: Int,
    val observedGenres: List<String>,
    // 1.0 = fresh; decays 0.05f per 10 ticks since snapshotDay; display fuzz scales with (1 - confidence).
    val confidence: Float,
    val snapshotDay: Int
)

@Serializable
enum class ReputationCommunity {
    INDIE_SCENE,
    COMMERCIAL,
    PRESS,
    VENUE_BOOKERS
}
