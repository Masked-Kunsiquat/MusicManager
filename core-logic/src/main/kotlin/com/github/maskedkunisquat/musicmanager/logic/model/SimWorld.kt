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
    val rivals: Map<String, RivalState> = emptyMap(),
    // Rival pursuit state — persisted so counters survive session restarts.
    val rivalProspectTargets: Map<String, String> = emptyMap(),   // rivalId -> prospectId
    val rivalProspectCounters: Map<String, Int> = emptyMap(),      // rivalId -> ticks on target
    val rivalPoachTargets: Map<String, String> = emptyMap(),       // rivalId -> artistId
    val rivalPoachCounters: Map<String, Int> = emptyMap(),         // rivalId -> ticks on poach
    val activeNegotiations: Map<String, Int> = emptyMap(),    // prospectId -> current round
    val activeRenewals: Map<String, Int> = emptyMap(),        // artistId -> current renewal round
    val unavailableProspects: Set<String> = emptySet(),        // cooldown after failed negotiation
    val chartSnapshot: MarketState = MarketState(emptyMap()),  // delayed market data; updates every 3 ticks
    // CapabilityType.name -> day the offer was last presented; prevents re-emit within cooldown window.
    val capabilityNoticedAt: Map<String, Int> = emptyMap(),
    // LabelNeedType.name -> day the warning was last emitted; prevents inbox flooding.
    val labelNeedNoticedAt: Map<String, Int> = emptyMap(),
    // prospectId -> day lead was passed; gated for 10 ticks before re-surfacing.
    val passedLeads: Map<String, Int> = emptyMap(),
    // prospectId -> day lead was watched; re-surfaces after 5 ticks with rawScore drift.
    val watchedLeads: Map<String, Int> = emptyMap(),
    // Prospects currently visible in the tape deck (not yet resolved by the player).
    val surfacedLeads: Set<String> = emptySet(),
    // Guards SeasonEnded from re-emitting on every tick after seasonEndTick is crossed.
    // Reset to false by NewSeasonInitializer when a new season begins.
    val seasonEndedEmitted: Boolean = false,
    val season: SeasonState = SeasonState(),
    // deadlineId -> Deadline; seeded at world init, status updated via MeetDeadline/ExtendDeadline effects
    // and by SimEngine when DeadlineMissed events are emitted.
    val deadlines: Map<String, Deadline> = emptyMap()
)
