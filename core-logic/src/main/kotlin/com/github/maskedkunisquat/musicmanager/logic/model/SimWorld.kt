package com.github.maskedkunisquat.musicmanager.logic.model

data class SimWorld(
    val seed: Long,
    val currentDay: Int,
    val artists: Map<String, ArtistState>,
    val label: LabelState,
    val market: MarketState,
    val contracts: Map<String, Contract>
)
