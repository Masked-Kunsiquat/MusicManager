package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState

internal fun decayNeeds(artist: ArtistState): ArtistState {
    val volatilityMultiplier = 0.5f + artist.dimensions.volatility
    val decayedNeeds = artist.needs.mapValues { (_, need) ->
        need.copy(value = (need.value - need.decayRate * volatilityMultiplier).coerceAtLeast(0f))
    }
    return artist.copy(needs = decayedNeeds)
}
