package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelState(
    val funds: Long,                                    // in cents
    val reputation: Map<ReputationCommunity, Float>,    // 0f..1f per community
    val rosterIds: Set<String>,
    val name: String = "Unnamed Label",
    val capabilities: Set<CapabilityType> = emptySet(),
    // genre -> preference weight (0-1); updated by PursueLead (+0.05) and PassLead (-0.03).
    val tasteVector: Map<String, Float> = emptyMap(),
    // rivalId -> last observed intel snapshot; populated by UpdateRivalIntel effect.
    val intelCache: Map<String, RivalSnapshot> = emptyMap()
)

// Cost in cents to rename the label, scaling with notoriety (total reputation across all communities).
// Floor: $1k (new labels). Ceiling: $25k (well-established labels).
fun labelRenameCost(label: LabelState): Long {
    val totalRep = label.reputation.values.sum()  // 0-4.0 range across 4 communities
    val dollars = (totalRep * 5_000.0).toLong().coerceIn(1_000L, 25_000L)
    return dollars * 100  // convert to cents
}

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
