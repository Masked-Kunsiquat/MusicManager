package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

private const val NEED_URGENT_THRESHOLD = 0.3f
private const val CONTRACT_EXPIRY_WARNING_DAYS = 30

internal fun generateEvents(world: SimWorld): List<SimEvent> = buildList {
    for (artist in world.artists.values) {
        addAll(needEvents(artist, world.currentDay))
        addAll(contractEvents(artist, world))
        addAll(wantEvents(artist, world.currentDay))
    }
}

private fun needEvents(artist: ArtistState, day: Int): List<SimEvent> =
    artist.needs.values
        .filter { it.value < NEED_URGENT_THRESHOLD }
        .map { need ->
            SimEvent.NeedUrgent(
                artistId = artist.id,
                needType = need.type,
                currentValue = need.value,
                dayOfGame = day
            )
        }

private fun contractEvents(artist: ArtistState, world: SimWorld): List<SimEvent> {
    val contractId = artist.contractId ?: return emptyList()
    val contract = world.contracts[contractId] ?: return emptyList()
    val daysRemaining = contract.expiryDay - world.currentDay
    if (daysRemaining > CONTRACT_EXPIRY_WARNING_DAYS) return emptyList()
    return listOf(
        SimEvent.ContractExpiring(
            artistId = artist.id,
            contractId = contractId,
            daysRemaining = daysRemaining,
            dayOfGame = world.currentDay
        )
    )
}

// Phase 1: activeWants is always empty until WorldInitializer populates it from archetypes.
private fun wantEvents(artist: ArtistState, day: Int): List<SimEvent> =
    artist.activeWants
        .filter { it.urgency >= 0.7f }
        .map { want ->
            SimEvent.WantSurfaced(
                artistId = artist.id,
                wantType = want.type,
                urgency = want.urgency,
                dayOfGame = day
            )
        }
