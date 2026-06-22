package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RivalSnapshot
import com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.logic.sim.RIVAL_POACH_THRESHOLD

// Returns the updated world and any events to immediately persist (e.g., NegotiationRound).
fun applyResponse(world: SimWorld, option: ResponseOption): Pair<SimWorld, List<SimEvent>> {
    require(option.costFunds >= 0L) { "costFunds must be non-negative, was ${option.costFunds}" }
    require(world.label.funds >= option.costFunds) {
        "Insufficient funds: need ${option.costFunds} cents, have ${world.label.funds}"
    }
    var updated = world.copy(
        label = world.label.copy(funds = world.label.funds - option.costFunds)
    )
    val injectedEvents = mutableListOf<SimEvent>()
    for (effect in option.effects) {
        val (newWorld, events) = applyEffect(updated, effect)
        updated = newWorld
        injectedEvents += events
    }
    // Stamp lastInteractionDay for any roster artist directly touched by this response.
    val touchedArtistIds: Set<String> = option.effects.mapNotNull { effect ->
        when (effect) {
            is StateEffect.NeedChange         -> effect.artistId
            is StateEffect.RelationshipChange -> effect.artistId
            is StateEffect.PairedNeedChange   -> effect.partnerId
            is StateEffect.OpenRenewal        -> effect.artistId
            is StateEffect.AdvanceRenewal     -> effect.artistId
            is StateEffect.RenewContract      -> effect.artistId
            is StateEffect.RenewalWalked      -> effect.artistId
            is StateEffect.WantSatisfied      -> effect.artistId
            else -> null
        }
    }.toSet()
    if (touchedArtistIds.isNotEmpty()) {
        updated = updated.copy(
            artists = updated.artists + touchedArtistIds.mapNotNull { id ->
                updated.artists[id]?.let { id to it.copy(lastInteractionDay = updated.currentDay) }
            }.toMap()
        )
    }
    return Pair(updated, injectedEvents)
}

private fun applyEffect(world: SimWorld, effect: StateEffect): Pair<SimWorld, List<SimEvent>> {
    val noEvents = emptyList<SimEvent>()
    return when (effect) {
        is StateEffect.NeedChange -> {
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            val need = artist.needs[effect.needType] ?: return Pair(world, noEvents)
            val newValue = (need.value + effect.delta).coerceIn(0f, 1f)
            Pair(world.copy(
                artists = world.artists + (effect.artistId to artist.copy(
                    needs = artist.needs + (effect.needType to need.copy(value = newValue))
                ))
            ), noEvents)
        }
        is StateEffect.LabelFundsChange -> {
            require(effect.delta >= 0L) { "LabelFundsChange delta must be non-negative, was ${effect.delta}" }
            Pair(world.copy(label = world.label.copy(funds = world.label.funds + effect.delta)), noEvents)
        }
        is StateEffect.RelationshipChange -> {
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            val newLoyalty = (artist.dimensions.loyalty + effect.delta).coerceIn(0f, 1f)
            Pair(world.copy(
                artists = world.artists + (effect.artistId to artist.copy(
                    dimensions = artist.dimensions.copy(loyalty = newLoyalty),
                    relationshipBalance = artist.relationshipBalance + effect.delta
                ))
            ), noEvents)
        }
        is StateEffect.RosterNeedChange -> {
            val updatedArtists = world.artists.mapValues { (_, artist) ->
                val need = artist.needs[effect.needType] ?: return@mapValues artist
                val newValue = (need.value + effect.delta).coerceIn(0f, 1f)
                artist.copy(needs = artist.needs + (effect.needType to need.copy(value = newValue)))
            }
            Pair(world.copy(artists = updatedArtists), noEvents)
        }
        is StateEffect.PairedNeedChange -> {
            require(effect.partnerId.isNotBlank()) { "PairedNeedChange.partnerId must be filled before resolving" }
            val artist = world.artists[effect.partnerId] ?: return Pair(world, noEvents)
            val need = artist.needs[effect.needType] ?: return Pair(world, noEvents)
            val newValue = (need.value + effect.delta).coerceIn(0f, 1f)
            Pair(world.copy(
                artists = world.artists + (effect.partnerId to artist.copy(
                    needs = artist.needs + (effect.needType to need.copy(value = newValue))
                ))
            ), noEvents)
        }
        is StateEffect.AdvanceNegotiation -> {
            if (effect.prospectId !in world.prospects || effect.prospectId in world.unavailableProspects) {
                return Pair(world, noEvents)
            }
            val nextRound = (world.activeNegotiations[effect.prospectId] ?: 0) + 1
            val newWorld = world.copy(
                activeNegotiations = world.activeNegotiations + (effect.prospectId to nextRound)
            )
            val event = SimEvent.NegotiationRound(
                prospectId = effect.prospectId,
                round = nextRound,
                dayOfGame = world.currentDay
            )
            Pair(newWorld, listOf(event))
        }
        is StateEffect.SignArtist -> {
            val prospect = world.prospects[effect.prospectId] ?: return Pair(world, noEvents)
            if (prospect.signability == SignabilityType.UNSIGNABLE) {
                // Prospect refuses to sign — clear the active negotiation but keep them in the
                // prospect pool with no cooldown so they remain available indefinitely.
                val newWorld = world.copy(
                    activeNegotiations = world.activeNegotiations - effect.prospectId
                )
                return Pair(newWorld, noEvents)
            }
            val artistId = "signed_${effect.prospectId}"
            val contractId = "contract_${effect.prospectId}"
            val volatility = prospect.dimensions.volatility
            val newArtist = ArtistState(
                id = artistId,
                name = prospect.name,
                genre = prospect.genre,
                dimensions = prospect.dimensions,
                needs = NeedType.entries.associateWith { needType ->
                    NeedState(
                        type = needType,
                        // High volatility → needs start lower (strained by transition)
                        value = 0.70f + (1f - volatility) * 0.25f,
                        decayRate = 0.02f + volatility * 0.03f
                    )
                },
                activeWants = emptyList(),
                contractId = contractId
            )
            val newContract = Contract(
                id = contractId,
                artistId = artistId,
                startDay = world.currentDay,
                expiryDay = world.currentDay + 180,
                revenueSplit = RevenueSplit(artistPercent = 50),
                creativeControl = CreativeControl.SHARED
            )
            val newWorld = world.copy(
                artists = world.artists + (artistId to newArtist),
                contracts = world.contracts + (contractId to newContract),
                label = world.label.copy(rosterIds = world.label.rosterIds + artistId),
                prospects = world.prospects - effect.prospectId,
                activeNegotiations = world.activeNegotiations - effect.prospectId
            )
            Pair(newWorld, noEvents)
        }
        is StateEffect.NegotiationFailed -> {
            val newWorld = world.copy(
                activeNegotiations = world.activeNegotiations - effect.prospectId,
                unavailableProspects = world.unavailableProspects + effect.prospectId
            )
            Pair(newWorld, noEvents)
        }
        is StateEffect.ReputationChange -> {
            val current = world.label.reputation[effect.community] ?: 0f
            val newValue = (current + effect.delta).coerceIn(0f, 1f)
            Pair(world.copy(
                label = world.label.copy(
                    reputation = world.label.reputation + (effect.community to newValue)
                )
            ), noEvents)
        }
        is StateEffect.UnlockCapability -> {
            Pair(world.copy(
                label = world.label.copy(
                    capabilities = world.label.capabilities + effect.type
                )
            ), noEvents)
        }
        is StateEffect.OpenRenewal -> {
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            if (artist.contractId != effect.contractId) return Pair(world, noEvents)
            // Guard: don't reset a renewal that's already in-flight (e.g. stale ContractExpiring email).
            if (world.activeRenewals.containsKey(effect.artistId)) return Pair(world, noEvents)
            val newWorld = world.copy(activeRenewals = world.activeRenewals + (effect.artistId to 1))
            val event = SimEvent.RenewalOpened(
                artistId = effect.artistId,
                contractId = effect.contractId,
                round = 1,
                dayOfGame = world.currentDay
            )
            Pair(newWorld, listOf(event))
        }
        is StateEffect.AdvanceRenewal -> {
            val currentRound = world.activeRenewals[effect.artistId] ?: return Pair(world, noEvents)
            val nextRound = currentRound + 1
            val newWorld = world.copy(activeRenewals = world.activeRenewals + (effect.artistId to nextRound))
            val event = SimEvent.RenewalOpened(
                artistId = effect.artistId,
                contractId = effect.contractId,
                round = nextRound,
                dayOfGame = world.currentDay
            )
            Pair(newWorld, listOf(event))
        }
        is StateEffect.RenewContract -> {
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            val oldContractId = artist.contractId
            val newContractId = "renewal_${effect.artistId}_${world.currentDay}_${effect.newExpiryTicks}"
            val newContract = Contract(
                id = newContractId,
                artistId = effect.artistId,
                startDay = world.currentDay,
                expiryDay = world.currentDay + effect.newExpiryTicks,
                revenueSplit = effect.revenueSplit,
                creativeControl = effect.creativeControl
            )
            val newWorld = world.copy(
                contracts = (if (oldContractId != null) world.contracts - oldContractId else world.contracts) +
                    (newContractId to newContract),
                artists = world.artists + (effect.artistId to artist.copy(contractId = newContractId)),
                activeRenewals = world.activeRenewals - effect.artistId
            )
            Pair(newWorld, noEvents)
        }
        is StateEffect.WantSatisfied -> {
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            if (artist.activeWants.none { it.type == effect.wantType }) return Pair(world, noEvents)
            val bonus = StateEffect.WantSatisfied.RELATIONSHIP_BONUS
            val newLoyalty = (artist.dimensions.loyalty + bonus).coerceIn(0f, 1f)
            Pair(world.copy(
                artists = world.artists + (effect.artistId to artist.copy(
                    activeWants = artist.activeWants.filter { it.type != effect.wantType },
                    dimensions = artist.dimensions.copy(loyalty = newLoyalty),
                    relationshipBalance = artist.relationshipBalance + bonus
                ))
            ), noEvents)
        }
        is StateEffect.PursueLead -> {
            val prospect = world.prospects[effect.prospectId] ?: return Pair(world, noEvents)
            if (effect.prospectId in world.unavailableProspects) return Pair(world, noEvents)
            val genre = prospect.genre
            val weight = (world.label.tasteVector[genre] ?: 0.5f)
            val newWorld1 = world.copy(
                label = world.label.copy(
                    tasteVector = world.label.tasteVector + (genre to (weight + 0.05f).coerceIn(0f, 1f))
                ),
                surfacedLeads = world.surfacedLeads - effect.prospectId
            )
            val nextRound = (newWorld1.activeNegotiations[effect.prospectId] ?: 0) + 1
            val newWorld2 = newWorld1.copy(
                activeNegotiations = newWorld1.activeNegotiations + (effect.prospectId to nextRound)
            )
            val event = SimEvent.NegotiationRound(
                prospectId = effect.prospectId, round = nextRound, dayOfGame = world.currentDay
            )
            Pair(newWorld2, listOf(event))
        }
        is StateEffect.PassLead -> {
            val prospect = world.prospects[effect.prospectId] ?: return Pair(world, noEvents)
            val genre = prospect.genre
            val weight = (world.label.tasteVector[genre] ?: 0.5f)
            Pair(world.copy(
                label = world.label.copy(
                    tasteVector = world.label.tasteVector + (genre to (weight - 0.03f).coerceIn(0f, 1f))
                ),
                passedLeads = world.passedLeads + (effect.prospectId to world.currentDay),
                surfacedLeads = world.surfacedLeads - effect.prospectId
            ), noEvents)
        }
        is StateEffect.UpdateRivalIntel -> {
            val rival = world.rivals[effect.rivalId] ?: return Pair(world, noEvents)
            // Rival roster isn't tracked individually in SimWorld; we observe genre focus weights.
            // Each genre the rival prioritises (weight >= 0.5) implies ~2 signed artists on average.
            val focusGenres = rival.genreWeights
                .filter { (_, w) -> w >= 0.5f }
                .keys
                .toList()
                .sorted()
            val estimatedSize = (focusGenres.size * 2).coerceAtLeast(1)
            val snapshot = RivalSnapshot(
                rivalId = effect.rivalId,
                observedRosterSize = estimatedSize,
                observedGenres = focusGenres,
                confidence = 1.0f,
                snapshotDay = world.currentDay
            )
            Pair(world.copy(
                label = world.label.copy(
                    intelCache = world.label.intelCache + (effect.rivalId to snapshot)
                )
            ), noEvents)
        }
        is StateEffect.WatchLead -> {
            val prospect = world.prospects[effect.prospectId] ?: return Pair(world, noEvents)
            val driftRaw = (prospect.id.hashCode().toLong() * world.currentDay) and 0xFF
            val drift = driftRaw.toFloat() / 255f * 0.10f - 0.05f  // [-0.05, +0.05]
            val newScore = (prospect.demo.rawScore + drift).coerceIn(0f, 1f)
            Pair(world.copy(
                prospects = world.prospects + (effect.prospectId to prospect.copy(
                    demo = prospect.demo.copy(rawScore = newScore)
                )),
                watchedLeads = world.watchedLeads + (effect.prospectId to world.currentDay),
                surfacedLeads = world.surfacedLeads - effect.prospectId
            ), noEvents)
        }
        is StateEffect.RenewalWalked -> {
            // Guard: only penalize if a renewal was actually in progress.
            if (effect.artistId !in world.activeRenewals) return Pair(world, noEvents)
            val artist = world.artists[effect.artistId] ?: return Pair(world, noEvents)
            val newLoyalty = (artist.dimensions.loyalty - 0.2f).coerceIn(0f, 1f)
            // Accelerate any rival already tracking this artist to threshold so the poach fires next tick.
            val acceleratedPoachCounters = world.rivals.keys
                .filter { rivalId -> world.rivalPoachTargets[rivalId] == effect.artistId }
                .fold(world.rivalPoachCounters) { counters, rivalId ->
                    counters + (rivalId to (RIVAL_POACH_THRESHOLD - 1))
                }
            Pair(world.copy(
                artists = world.artists + (effect.artistId to artist.copy(
                    dimensions = artist.dimensions.copy(loyalty = newLoyalty),
                    relationshipBalance = artist.relationshipBalance - 0.2f
                )),
                activeRenewals = world.activeRenewals - effect.artistId,
                rivalPoachCounters = acceleratedPoachCounters
            ), noEvents)
        }
    }
}
