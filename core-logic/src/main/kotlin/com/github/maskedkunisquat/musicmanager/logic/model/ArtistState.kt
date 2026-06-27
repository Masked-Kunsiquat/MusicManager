package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistState(
    val id: String,
    val name: String,
    val genre: String,
    val dimensions: ArtistDimensions,
    val needs: Map<NeedType, NeedState>,
    val activeWants: List<Want>,
    val contractId: String?,
    // Running sum of all RelationshipChange deltas — drives renewal offer weighting.
    // Unclamped; negative = strained history, positive = warm history.
    val relationshipBalance: Float = 0f,
    // WantType.name → day the want was last surfaced; prevents re-surfacing within cooldown window.
    val wantLastSurfacedAt: Map<String, Int> = emptyMap(),
    // Day of the last player-resolved event touching this artist; drives recency descriptor.
    val lastInteractionDay: Int = 0,
    // NeedType.name → day the need was last notified; prevents re-emission within cooldown window.
    val needNotifiedAt: Map<String, Int> = emptyMap()
)
