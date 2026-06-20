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
    // Defaults so snapshots written before these fields existed still deserialize.
    val prospects: Map<String, ProspectState> = emptyMap(),
    val scouts: Map<String, ScoutState> = emptyMap(),
    val activeNegotiations: Map<String, Int> = emptyMap(),    // prospectId → current round
    val unavailableProspects: Set<String> = emptySet(),        // cooldown after failed negotiation
    val chartSnapshot: MarketState = MarketState(emptyMap())   // delayed market data; updates every 3 ticks
)
