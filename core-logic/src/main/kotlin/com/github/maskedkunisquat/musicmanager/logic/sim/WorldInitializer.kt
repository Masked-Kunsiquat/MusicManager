package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

object WorldInitializer {

    private const val CENTS_PER_DOLLAR = 100L
    private val GENRES = listOf("indie-rock", "pop", "hip-hop", "electronic", "folk", "r&b")
    private val ADJECTIVES = listOf("Young", "Dark", "Golden", "Wild", "Restless", "Silent", "Bright", "New")
    private val NOUNS = listOf("Lions", "Birds", "Waves", "Stars", "Flowers", "Rivers", "Tides", "Ghosts")

    fun initializeWorld(seed: Long): SimWorld {
        val rng = Random(seed)
        val artistCount = 3 + rng.nextInt(3) // 3–5

        val contracts = mutableMapOf<String, Contract>()
        val artists = (0 until artistCount).associate { i ->
            val artistId = "artist_${seed}_$i"
            val contractId = "contract_${seed}_$i"
            contracts[contractId] = buildContract(artistId, contractId, rng)
            artistId to buildArtist(artistId, contractId, rng)
        }

        return SimWorld(
            seed = seed,
            currentDay = 0,
            artists = artists,
            label = buildLabel(artists.keys.toSet(), rng),
            market = buildMarket(rng),
            contracts = contracts
        )
    }

    private fun buildArtist(id: String, contractId: String, rng: Random): ArtistState = ArtistState(
        id = id,
        name = "${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}",
        genre = GENRES.random(rng),
        dimensions = ArtistDimensions(
            confidence = rng.nextFloat(),
            commercialAppetite = rng.nextFloat(),
            volatility = rng.nextFloat(),
            loyalty = rng.nextFloat()
        ),
        needs = NeedType.entries.associateWith { needType ->
            NeedState(
                type = needType,
                value = 0.7f + rng.nextFloat() * 0.3f,   // start well-satisfied
                decayRate = 0.02f + rng.nextFloat() * 0.03f
            )
        },
        activeWants = emptyList(), // Phase 1: populate from artist archetype + context
        contractId = contractId
    )

    private fun buildContract(artistId: String, contractId: String, rng: Random): Contract = Contract(
        id = contractId,
        artistId = artistId,
        startDay = 0,
        expiryDay = 180 + rng.nextInt(181),             // 6–12 months of ticks
        revenueSplit = RevenueSplit(artistPercent = 40 + rng.nextInt(21)),  // 40–60%
        creativeControl = CreativeControl.entries.random(rng)
    )

    private fun buildLabel(rosterIds: Set<String>, rng: Random): LabelState = LabelState(
        funds = rng.nextLong(50_000 * CENTS_PER_DOLLAR, 100_000 * CENTS_PER_DOLLAR),
        reputation = ReputationCommunity.entries.associateWith { 0.3f + rng.nextFloat() * 0.2f },
        rosterIds = rosterIds
    )

    private fun buildMarket(rng: Random): MarketState = MarketState(
        genreTrends = GENRES.associateWith { rng.nextFloat() }
    )
}
