package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect

fun applyResponse(world: SimWorld, option: ResponseOption): SimWorld {
    require(option.costFunds >= 0L) { "costFunds must be non-negative, was ${option.costFunds}" }
    require(world.label.funds >= option.costFunds) {
        "Insufficient funds: need ${option.costFunds} cents, have ${world.label.funds}"
    }
    var updated = world.copy(
        label = world.label.copy(funds = world.label.funds - option.costFunds)
    )
    for (effect in option.effects) {
        updated = applyEffect(updated, effect)
    }
    return updated
}

private fun applyEffect(world: SimWorld, effect: StateEffect): SimWorld = when (effect) {
    is StateEffect.NeedChange -> {
        val artist = world.artists[effect.artistId] ?: return world
        val need = artist.needs[effect.needType] ?: return world
        val newValue = (need.value + effect.delta).coerceIn(0f, 1f)
        world.copy(
            artists = world.artists + (effect.artistId to artist.copy(
                needs = artist.needs + (effect.needType to need.copy(value = newValue))
            ))
        )
    }
    is StateEffect.LabelFundsChange -> {
        // LabelFundsChange is income only — debits go through costFunds on ResponseOption.
        require(effect.delta >= 0L) { "LabelFundsChange delta must be non-negative, was ${effect.delta}" }
        world.copy(label = world.label.copy(funds = world.label.funds + effect.delta))
    }
    is StateEffect.RelationshipChange -> {
        val artist = world.artists[effect.artistId] ?: return world
        val newLoyalty = (artist.dimensions.loyalty + effect.delta).coerceIn(0f, 1f)
        world.copy(
            artists = world.artists + (effect.artistId to artist.copy(
                dimensions = artist.dimensions.copy(loyalty = newLoyalty)
            ))
        )
    }
    is StateEffect.RosterNeedChange -> {
        val updatedArtists = world.artists.mapValues { (_, artist) ->
            val need = artist.needs[effect.needType] ?: return@mapValues artist
            val newValue = (need.value + effect.delta).coerceIn(0f, 1f)
            artist.copy(needs = artist.needs + (effect.needType to need.copy(value = newValue)))
        }
        world.copy(artists = updatedArtists)
    }
}
