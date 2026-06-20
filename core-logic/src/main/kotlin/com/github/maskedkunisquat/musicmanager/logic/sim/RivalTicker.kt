package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RivalState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

internal const val RIVAL_PROSPECT_THRESHOLD = 10  // ticks to sign a prospect
internal const val RIVAL_POACH_THRESHOLD = 8       // ticks to poach a signed artist
private const val PRESS_REP_PENALTY = -0.03f

// Stateful per-session: counters reset on world reload (rivals restart their clock).
// rivalProgress and poachProgress state is held here, not on SimWorld, per design.
internal class RivalTicker {

    // rivalId → prospectId currently being pursued
    private val prospectTargets = mutableMapOf<String, String>()
    private val prospectCounters = mutableMapOf<String, Int>()

    // rivalId → artistId currently being poached
    private val poachTargets = mutableMapOf<String, String>()
    private val poachCounters = mutableMapOf<String, Int>()

    fun tick(world: SimWorld, rng: Random): Pair<SimWorld, List<SimEvent>> {
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
        invalidateProspectTargetIfGone(rivalId, world)
        if (rivalId !in prospectTargets) {
            val target = pickProspect(rival, world, rng) ?: return world to emptyList()
            prospectTargets[rivalId] = target.id
            prospectCounters[rivalId] = 0
        }
        val targetId = prospectTargets[rivalId]!!
        val counter = (prospectCounters[rivalId] ?: 0) + 1
        prospectCounters[rivalId] = counter

        if (counter < RIVAL_PROSPECT_THRESHOLD) return world to emptyList()

        val prospect = world.prospects[targetId] ?: run {
            clearProspectTarget(rivalId); return world to emptyList()
        }
        val wasPlayerTarget = targetId in world.activeNegotiations
        val updatedWorld = world.copy(
            prospects = world.prospects - targetId,
            label = world.label.copy(reputation = penalizePress(world))
        )
        clearProspectTarget(rivalId)
        return updatedWorld to listOf(
            SimEvent.RivalSigning(
                rivalId = rivalId,
                rivalName = rival.name,
                prospectName = prospect.name,
                genre = prospect.genre,
                wasPlayerTarget = wasPlayerTarget,
                dayOfGame = world.currentDay
            )
        )
    }

    private fun invalidateProspectTargetIfGone(rivalId: String, world: SimWorld) {
        val t = prospectTargets[rivalId] ?: return
        if (t !in world.prospects || t in world.unavailableProspects) clearProspectTarget(rivalId)
    }

    private fun clearProspectTarget(rivalId: String) {
        prospectTargets.remove(rivalId); prospectCounters.remove(rivalId)
    }

    private fun pickProspect(rival: RivalState, world: SimWorld, rng: Random): ProspectState? {
        val available = world.prospects.values.filter { it.id !in world.unavailableProspects }
        if (available.isEmpty()) return null
        return available.maxByOrNull { p ->
            val w = rival.genreWeights[p.genre] ?: 0.1f
            p.signabilityScore * w + rng.nextFloat() * 0.05f  // small noise for tie-breaking
        }
    }

    // --- Poach pursuit ---

    private fun processPoachPursuit(
        rivalId: String,
        rival: RivalState,
        world: SimWorld
    ): Pair<SimWorld, List<SimEvent>> {
        invalidatePoachTargetIfGone(rivalId, world)
        if (rivalId !in poachTargets) {
            val target = pickPoachTarget(rival, world) ?: return world to emptyList()
            poachTargets[rivalId] = target.id
            poachCounters[rivalId] = 0
        }
        val targetId = poachTargets[rivalId]!!
        val artist = world.artists[targetId] ?: run {
            clearPoachTarget(rivalId); return world to emptyList()
        }

        // Drop target if conditions no longer met (loyalty recovered or contract renewed).
        if (!meetsPoachCondition(artist, world)) {
            clearPoachTarget(rivalId); return world to emptyList()
        }

        val counter = (poachCounters[rivalId] ?: 0) + 1
        poachCounters[rivalId] = counter
        if (counter < RIVAL_POACH_THRESHOLD) return world to emptyList()

        val updatedWorld = world.copy(
            artists = world.artists - targetId,
            contracts = if (artist.contractId != null) world.contracts - artist.contractId!! else world.contracts,
            label = world.label.copy(
                rosterIds = world.label.rosterIds - targetId,
                reputation = penalizePress(world)
            )
        )
        clearPoachTarget(rivalId)
        return updatedWorld to listOf(
            SimEvent.RivalPoach(
                rivalId = rivalId,
                rivalName = rival.name,
                artistId = targetId,
                artistName = artist.name,
                dayOfGame = world.currentDay
            )
        )
    }

    private fun invalidatePoachTargetIfGone(rivalId: String, world: SimWorld) {
        val t = poachTargets[rivalId] ?: return
        if (t !in world.artists) clearPoachTarget(rivalId)
    }

    private fun clearPoachTarget(rivalId: String) {
        poachTargets.remove(rivalId); poachCounters.remove(rivalId)
    }

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
}
