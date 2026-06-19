package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class SimWorld(
    val seed: Long,
    val currentDay: Int,
    val artists: Map<String, ArtistState>,
    val label: LabelState,
    val market: MarketState,
    val contracts: Map<String, Contract>,
    // Default emptyMap so snapshots written before 2-A-4 still deserialize.
    val prospects: Map<String, ProspectState> = emptyMap()
)
