package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.SeasonFacts
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonSummary
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld

object SeasonSummaryEvaluator {

    fun evaluate(world: SimWorld, facts: SeasonFacts): SeasonSummary {
        val lostIds = facts.rivalPoachArtistIds + facts.renewalWalkedArtistIds
        return SeasonSummary(
            seasonNumber = world.season.seasonNumber,
            artistsRetained = world.artists.size,
            artistsLost = lostIds.size,
            deadlinesMet = facts.deadlinesMet,
            deadlinesMissed = facts.deadlinesMissed,
            fundsNet = world.label.funds - world.season.startFunds,
            reputationDeltas = world.label.reputation.entries.associate { (community, current) ->
                community.name to (current - (world.season.startReputation[community.name] ?: current))
            },
            departedArtistNames = facts.departedArtistNames
        )
    }
}
