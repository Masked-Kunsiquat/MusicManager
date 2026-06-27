package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonState
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

object NewSeasonInitializer {

    // Label keeps 60% of end-season funds; floor prevents a death spiral after a bad season.
    private const val FUNDS_FLOOR_CENTS = 10_000 * 100L  // $10,000

    fun advance(world: SimWorld): SimWorld {
        val newSeasonNumber = world.season.seasonNumber + 1
        // XOR with the new season number produces a deterministic but varied seed each season.
        val newSeed = world.seed xor newSeasonNumber.toLong()
        val rng = Random(newSeed)

        // --- Reputation: carries unchanged — it is the single durable cross-season asset ---
        // (label.reputation is copied as-is via label.copy() below)

        // --- Roster: artists carry over with relationship memory decay and fresh wants ---
        val newArtists = world.artists.mapValues { (_, artist) ->
            artist.copy(
                // Memory fades, not wipes — half the accumulated goodwill/friction carries.
                relationshipBalance = artist.relationshipBalance * 0.5f,
                // Fresh season, fresh goals — re-derive from dimensions, not carried over.
                activeWants = WorldInitializer.buildArtistWants(artist.dimensions),
                lastInteractionDay = 0,
                wantLastSurfacedAt = emptyMap(),
                needNotifiedAt = emptyMap()
            )
        }

        // --- Contracts: rebase expiry ticks relative to the new season start (tick 0) ---
        // A contract with 60 ticks remaining at season-1 end becomes expiryDay=60 in season 2.
        // Lapsed contracts (remaining ≤ 0) get a grace period of 1 tick so they surface immediately.
        val newContracts = world.contracts.mapValues { (_, contract) ->
            val remaining = (contract.expiryDay - world.season.seasonEndTick).coerceAtLeast(1)
            contract.copy(startDay = 0, expiryDay = remaining)
        }

        // --- Funds: partial carry, floor at $10,000 to prevent unrecoverable state ---
        // Integer arithmetic avoids float precision loss on cent-denominated Long values.
        val newFunds = (world.label.funds * 60L / 100L).coerceAtLeast(FUNDS_FLOOR_CENTS)

        // --- Label: capabilities and tasteVector persist; intelCache clears (rivals reshuffle) ---
        val newLabel = world.label.copy(
            funds = newFunds,
            rosterIds = newArtists.keys,
            intelCache = emptyMap()
        )

        // --- Prospects: fresh pool from the new seed, unique adjectives per season ---
        val prospectCount = 6 + rng.nextInt(5)  // 6-10
        val nextName = WorldInitializer.namePicker(rng)
        val newProspects = (0 until prospectCount).associate { i ->
            val id = "prospect_${newSeed}_$i"
            id to WorldInitializer.buildProspect(id, nextName(), rng)
        } + run {
            val id = "prospect_${newSeed}_whale"
            mapOf(id to WorldInitializer.buildUnsignableProspect(id, nextName(), rng))
        }

        // --- Rivals: fresh from new seed (talent landscape shifts each season) ---
        val newRivals = (0 until 2).associate { i ->
            val id = "rival_${newSeed}_$i"
            id to WorldInitializer.buildRival(id, i, rng)
        }

        // --- Deadlines: fresh set for the new season ---
        val newDeadlines = WorldInitializer.buildDeadlines(newArtists.keys, rng, newSeasonNumber)

        // --- Scouts: employees carry over; lastReportDay re-staggered so they don't burst together ---
        // Sort by ID before mapIndexed to ensure the stagger offset is deterministic regardless
        // of map implementation iteration order.
        val newScouts = world.scouts.entries.sortedBy { it.key }
            .mapIndexed { index, (id, scout) ->
                id to scout.copy(lastReportDay = -(index * (SCOUT_REPORT_INTERVAL / 2)))
            }.toMap()

        // --- Market: soft-reset — each trend drifts 50% toward 0.5 (structural bias carries, extremes blunted) ---
        val newGenreTrends = world.market.genreTrends.mapValues { (_, trend) ->
            trend + (0.5f - trend) * 0.5f
        }

        val newSeasonState = SeasonState(
            seasonNumber = newSeasonNumber,
            seasonStartTick = 0,
            seasonEndTick = 90,
            startFunds = newFunds,
            startReputation = newLabel.reputation.mapKeys { it.key.name }
        )

        // Everything not explicitly carried: activeRenewals, activeNegotiations,
        // unavailableProspects, passedLeads, watchedLeads, surfacedLeads,
        // rivalProspect/PoachTargets/Counters, capabilityNoticedAt, labelNeedNoticedAt
        // all default to empty via SimWorld defaults.
        return SimWorld(
            seed = newSeed,
            currentDay = 0,
            artists = newArtists,
            label = newLabel,
            market = MarketState(genreTrends = newGenreTrends),
            contracts = newContracts,
            prospects = newProspects,
            scouts = newScouts,
            rivals = newRivals,
            season = newSeasonState,
            deadlines = newDeadlines,
            seasonEndedEmitted = false
        )
    }
}
