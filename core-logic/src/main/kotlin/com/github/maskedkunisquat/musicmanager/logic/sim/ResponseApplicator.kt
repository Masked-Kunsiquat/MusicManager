package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect

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
                    dimensions = artist.dimensions.copy(loyalty = newLoyalty)
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
    }
}
