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
    val relationshipBalance: Float = 0f
)
