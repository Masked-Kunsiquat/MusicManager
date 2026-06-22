package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RivalState
import com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

internal const val RIVAL_PROSPECT_THRESHOLD = 10
internal const val RIVAL_POACH_THRESHOLD = 8
private const val PRESS_REP_PENALTY = -0.03f

// Pure function — all counter state lives on SimWorld so it survives session restarts.
internal fun tickRivals(world: SimWorld, rng: Random): Pair<SimWorld, List<SimEvent>> {
    if (world.rivals.isEmpty()) return world to emptyList()
    var current = world
    val events = mutableListOf<SimEvent>()
    for ((rivalId, rival) in world.rivals) {
        val (w1, e1) = processProspectPursuit(rivalId, rival, current, rng)
        current = w1; events += e1
        val (w2, e2) = processPoachPursuit(rivalId, rival, current)
        current = w2; events += e2
    }
    return current to events
}

// --- Prospect pursuit ---

private fun processProspectPursuit(
    rivalId: String,
    rival: RivalState,
    world: SimWorld,
    rng: Random
): Pair<SimWorld, List<SimEvent>> {
    val world1 = invalidateProspectTargetIfGone(rivalId, world)
    val targetId = world1.rivalProspectTargets[rivalId] ?: run {
        val target = pickProspect(rival, world1, rng) ?: return world1 to emptyList()
        return processProspectPursuit(rivalId, rival,
            world1.copy(
                rivalProspectTargets = world1.rivalProspectTargets + (rivalId to target.id),
                rivalProspectCounters = world1.rivalProspectCounters + (rivalId to 0)
            ), rng)
    }

    val counter = (world1.rivalProspectCounters[rivalId] ?: 0) + 1
    val world2 = world1.copy(rivalProspectCounters = world1.rivalProspectCounters + (rivalId to counter))

    if (counter < RIVAL_PROSPECT_THRESHOLD) return world2 to emptyList()

    val prospect = world2.prospects[targetId] ?: return world2.clearProspectTarget(rivalId) to emptyList()
    val wasPlayerTarget = targetId in world2.activeNegotiations
    val world3 = world2.clearProspectTarget(rivalId).copy(
        prospects = world2.prospects - targetId,
        label = world2.label.copy(reputation = penalizePress(world2))
    )
    return world3 to listOf(
        SimEvent.RivalSigning(
            rivalId = rivalId,
            rivalName = rival.name,
            prospectName = prospect.name,
            genre = prospect.genre,
            wasPlayerTarget = wasPlayerTarget,
            dayOfGame = world2.currentDay
        )
    )
}

private fun invalidateProspectTargetIfGone(rivalId: String, world: SimWorld): SimWorld {
    val t = world.rivalProspectTargets[rivalId] ?: return world
    return if (t !in world.prospects || t in world.unavailableProspects) world.clearProspectTarget(rivalId) else world
}

private fun SimWorld.clearProspectTarget(rivalId: String) = copy(
    rivalProspectTargets = rivalProspectTargets - rivalId,
    rivalProspectCounters = rivalProspectCounters - rivalId
)

private fun pickProspect(rival: RivalState, world: SimWorld, rng: Random): ProspectState? {
    val available = world.prospects.values.filter {
        it.id !in world.unavailableProspects && it.signability != SignabilityType.UNSIGNABLE
    }
    if (available.isEmpty()) return null
    return available.maxByOrNull { p ->
        val w = rival.genreWeights[p.genre] ?: 0.1f
        p.signabilityScore * w + rng.nextFloat() * 0.05f
    }
}

// --- Poach pursuit ---

private fun processPoachPursuit(
    rivalId: String,
    rival: RivalState,
    world: SimWorld
): Pair<SimWorld, List<SimEvent>> {
    val world1 = invalidatePoachTargetIfGone(rivalId, world)
    val targetId = world1.rivalPoachTargets[rivalId] ?: run {
        val target = pickPoachTarget(rival, world1) ?: return world1 to emptyList()
        return processPoachPursuit(rivalId, rival,
            world1.copy(
                rivalPoachTargets = world1.rivalPoachTargets + (rivalId to target.id),
                rivalPoachCounters = world1.rivalPoachCounters + (rivalId to 0)
            ))
    }

    val artist = world1.artists[targetId] ?: return world1.clearPoachTarget(rivalId) to emptyList()

    if (!meetsPoachCondition(artist, world1)) {
        return world1.clearPoachTarget(rivalId) to emptyList()
    }

    val counter = (world1.rivalPoachCounters[rivalId] ?: 0) + 1
    val world2 = world1.copy(rivalPoachCounters = world1.rivalPoachCounters + (rivalId to counter))

    if (counter < RIVAL_POACH_THRESHOLD) return world2 to emptyList()

    val world3 = world2.clearPoachTarget(rivalId).copy(
        artists = world2.artists - targetId,
        contracts = if (artist.contractId != null) world2.contracts - artist.contractId!! else world2.contracts,
        label = world2.label.copy(
            rosterIds = world2.label.rosterIds - targetId,
            reputation = penalizePress(world2)
        )
    )
    return world3 to listOf(
        SimEvent.RivalPoach(
            rivalId = rivalId,
            rivalName = rival.name,
            artistId = targetId,
            artistName = artist.name,
            dayOfGame = world2.currentDay
        )
    )
}

private fun invalidatePoachTargetIfGone(rivalId: String, world: SimWorld): SimWorld {
    val t = world.rivalPoachTargets[rivalId] ?: return world
    return if (t !in world.artists) world.clearPoachTarget(rivalId) else world
}

private fun SimWorld.clearPoachTarget(rivalId: String) = copy(
    rivalPoachTargets = rivalPoachTargets - rivalId,
    rivalPoachCounters = rivalPoachCounters - rivalId
)

private fun meetsPoachCondition(artist: ArtistState, world: SimWorld): Boolean {
    if (artist.dimensions.loyalty >= 0.3f) return false
    val contract = world.contracts[artist.contractId] ?: return false
    return (contract.expiryDay - world.currentDay) <= 15
}

private fun pickPoachTarget(rival: RivalState, world: SimWorld): ArtistState? =
    world.artists.values
        .filter { meetsPoachCondition(it, world) }
        .maxByOrNull { artist -> rival.genreWeights[artist.genre] ?: 0.1f }

private fun penalizePress(world: SimWorld): Map<ReputationCommunity, Float> {
    val current = world.label.reputation[ReputationCommunity.PRESS] ?: 0f
    return world.label.reputation + (ReputationCommunity.PRESS to (current + PRESS_REP_PENALTY).coerceIn(0f, 1f))
}
