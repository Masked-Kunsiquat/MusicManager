package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistDimensions
import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.Contract
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.Deadline
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.DemoState
import com.github.maskedkunisquat.musicmanager.logic.model.LabelState
import com.github.maskedkunisquat.musicmanager.logic.model.MarketState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedState
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ProspectState
import com.github.maskedkunisquat.musicmanager.logic.model.SeasonState
import com.github.maskedkunisquat.musicmanager.logic.model.Want
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.SignabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.RivalState
import com.github.maskedkunisquat.musicmanager.logic.model.ScoutState
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.random.Random

object WorldInitializer {

    private const val CENTS_PER_DOLLAR = 100L
    private val GENRES = listOf("indie-rock", "pop", "hip-hop", "electronic", "folk", "r&b")
    private val ADJECTIVES = listOf("Young", "Dark", "Golden", "Wild", "Restless", "Silent", "Bright", "New")
    private val NOUNS = listOf("Lions", "Birds", "Waves", "Stars", "Flowers", "Rivers", "Tides", "Ghosts")
    private val SCOUT_FIRST = listOf("Marcus", "Nina", "Dara", "Leon", "Priya", "Eli", "Tanya", "Jax")
    private val SCOUT_LAST = listOf("Cross", "Webb", "Reid", "Park", "Osei", "Vance", "Cole", "Marsh")
    private val RIVAL_NAMES = listOf("Mercury Sound", "Parallax Records", "Horizon Group", "Crestfall", "Vertex Label", "Meridian Music")
    private val DEMO_DESCRIPTORS = mapOf(
        "indie-rock" to listOf("lo-fi bedroom pop", "driving alt-rock", "sun-bleached indie", "noise-pop buzz"),
        "pop"        to listOf("stadium shimmer", "candy gloss pop", "breezy electro-pop", "confessional power-pop"),
        "hip-hop"    to listOf("street-corner rap", "jazzy boom-bap", "trap soul hybrid", "conscious flow"),
        "electronic" to listOf("club-ready techno", "ethereal ambient", "synth-pop glow", "dark wave pulse"),
        "folk"       to listOf("campfire storytelling", "sparse Appalachian", "indie folk warmth", "finger-picked dream"),
        "r&b"        to listOf("late-night smooth r&b", "neo-soul warmth", "gospel-tinged r&b", "bedroom r&b haze")
    )

    fun initializeWorld(seed: Long): SimWorld {
        val rng = Random(seed)
        val artistCount = 3 + rng.nextInt(3) // 3-5

        val contracts = mutableMapOf<String, Contract>()
        val artists = (0 until artistCount).associate { i ->
            val artistId = "artist_${seed}_$i"
            val contractId = "contract_${seed}_$i"
            contracts[contractId] = buildContract(artistId, contractId, rng)
            artistId to buildArtist(artistId, contractId, rng)
        }

        val prospectCount = 6 + rng.nextInt(5) // 6-10
        val prospects = (0 until prospectCount).associate { i ->
            val id = "prospect_${seed}_$i"
            id to buildProspect(id, rng)
        } + run {
            // One permanently unsignable prospect per world -- high signabilityScore so scouts
            // surface them often, but SignArtist always bounces. Intended as a recurring tease.
            val id = "prospect_${seed}_whale"
            mapOf(id to buildUnsignableProspect(id, rng))
        }

        // Two scouts, staggered by half the report interval so reports don't burst together.
        val scouts = (0 until 2).associate { i ->
            val id = "scout_${seed}_$i"
            id to buildScout(id, i, rng)
        }

        val rivals = (0 until 2).associate { i ->
            val id = "rival_${seed}_$i"
            id to buildRival(id, i, rng)
        }

        val deadlines = buildDeadlines(artists.keys, rng, seasonNumber = 1)
        val label = buildLabel(artists.keys.toSet(), rng)

        return SimWorld(
            seed = seed,
            currentDay = 0,
            artists = artists,
            label = label,
            market = buildMarket(rng),
            contracts = contracts,
            prospects = prospects,
            scouts = scouts,
            rivals = rivals,
            season = SeasonState(
                seasonNumber = 1,
                seasonStartTick = 0,
                seasonEndTick = 180,
                startFunds = label.funds,
                startReputation = label.reputation.mapKeys { it.key.name }
            ),
            deadlines = deadlines
        )
    }

    private fun buildArtist(id: String, contractId: String, rng: Random): ArtistState {
        val name = "${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
        val genre = GENRES.random(rng)
        val dimensions = ArtistDimensions(
            confidence = rng.nextFloat(),
            commercialAppetite = rng.nextFloat(),
            volatility = rng.nextFloat(),
            loyalty = rng.nextFloat()
        )
        val needs = NeedType.entries.associateWith { needType ->
            NeedState(
                type = needType,
                value = 0.7f + rng.nextFloat() * 0.3f,
                decayRate = 0.02f + rng.nextFloat() * 0.03f
            )
        }
        return ArtistState(
            id = id,
            name = name,
            genre = genre,
            dimensions = dimensions,
            needs = needs,
            activeWants = buildArtistWants(dimensions),
            contractId = contractId
        )
    }

    // Derives 0-2 wants from the artist's dimension profile. Pure function -- no RNG.
    // Rule: financial anxiety (low loyalty) surfaces first; career wants (confidence,
    // volatility) fill remaining slots up to a cap of 2.
    internal fun buildArtistWants(d: ArtistDimensions): List<Want> {
        val wants = mutableListOf<Want>()
        if (d.loyalty < 0.40f) {
            wants += Want(
                type = WantType.INCREASED_ROYALTIES,
                urgency = (0.70f + (0.40f - d.loyalty) * 1.25f).coerceIn(0f, 1f),
                expiryDay = null
            )
        }
        if (wants.size < 2 && d.confidence >= 0.65f) {
            val type = if (d.commercialAppetite >= 0.60f) WantType.RECORD_ALBUM else WantType.MAJOR_VENUE_TOUR
            wants += Want(type = type, urgency = (0.55f + d.confidence * 0.30f).coerceIn(0f, 1f), expiryDay = null)
        }
        if (wants.size < 2 && d.volatility >= 0.70f) {
            wants += Want(type = WantType.GENRE_EXPERIMENT, urgency = (0.50f + d.volatility * 0.35f).coerceIn(0f, 1f), expiryDay = null)
        }
        if (wants.size < 2 && d.confidence in 0.45f..<0.65f && d.commercialAppetite < 0.45f) {
            wants += Want(type = WantType.COLLAB_WITH_PRODUCER, urgency = (0.60f + d.confidence * 0.25f).coerceIn(0f, 1f), expiryDay = null)
        }
        return wants
    }

    private fun buildContract(artistId: String, contractId: String, rng: Random): Contract = Contract(
        id = contractId,
        artistId = artistId,
        startDay = 0,
        expiryDay = 180 + rng.nextInt(181),             // 6-12 months of ticks
        revenueSplit = RevenueSplit(artistPercent = 40 + rng.nextInt(21)),  // 40-60%
        creativeControl = CreativeControl.entries.random(rng)
    )

    private fun buildLabel(rosterIds: Set<String>, rng: Random): LabelState = LabelState(
        funds = rng.nextLong(50_000 * CENTS_PER_DOLLAR, 100_000 * CENTS_PER_DOLLAR),
        reputation = ReputationCommunity.entries.associateWith { 0.3f + rng.nextFloat() * 0.2f },
        rosterIds = rosterIds,
        tasteVector = GENRES.associateWith { 0.5f }
    )

    private fun buildMarket(rng: Random): MarketState = MarketState(
        genreTrends = GENRES.associateWith { rng.nextFloat() }
    )

    internal fun buildProspect(id: String, rng: Random): ProspectState {
        val name = "${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
        val genre = GENRES.random(rng)
        val dims = ArtistDimensions(
            confidence = rng.nextFloat(),
            commercialAppetite = rng.nextFloat(),
            volatility = rng.nextFloat(),
            loyalty = rng.nextFloat()
        )
        val signabilityScore = 0.2f + rng.nextFloat() * 0.7f  // 0.2-0.9: avoids trivially impossible or easy negotiations
        val demo = DemoState(
            descriptor = DEMO_DESCRIPTORS[genre]?.random(rng) ?: "$genre demo",
            rawScore = 0.2f + rng.nextFloat() * 0.7f,
            submittedDay = 0
        )
        return ProspectState(id = id, name = name, genre = genre, dimensions = dims,
            signabilityScore = signabilityScore, demo = demo)
    }

    internal fun buildUnsignableProspect(id: String, rng: Random): ProspectState {
        val name = "${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
        val genre = GENRES.random(rng)
        val dims = ArtistDimensions(
            confidence = 0.7f + rng.nextFloat() * 0.3f,
            commercialAppetite = rng.nextFloat() * 0.25f,  // low commercial appetite
            volatility = rng.nextFloat() * 0.3f,           // steady -- consistent in their refusal
            loyalty = rng.nextFloat() * 0.3f
        )
        val signabilityScore = 0.90f + rng.nextFloat() * 0.10f  // scouts surface them constantly
        val demo = DemoState(
            descriptor = DEMO_DESCRIPTORS[genre]?.random(rng) ?: "$genre demo",
            rawScore = 0.2f + rng.nextFloat() * 0.7f,
            submittedDay = 0
        )
        return ProspectState(id = id, name = name, genre = genre, dimensions = dims,
            signabilityScore = signabilityScore, signability = SignabilityType.UNSIGNABLE, demo = demo)
    }

    internal fun buildRival(id: String, index: Int, rng: Random): RivalState {
        val focusCount = 2 + rng.nextInt(2)  // 2-3 focus genres per rival
        val focusGenres = (0 until focusCount).map { GENRES.random(rng) }.toSet()
        val genreWeights = GENRES.associateWith { genre ->
            if (genre in focusGenres) 0.60f + rng.nextFloat() * 0.40f  // 0.60-1.00
            else 0.05f + rng.nextFloat() * 0.25f                         // 0.05-0.30
        }
        return RivalState(
            id = id,
            name = RIVAL_NAMES[index % RIVAL_NAMES.size],
            genreWeights = genreWeights
        )
    }

    internal fun buildDeadlines(artistIds: Set<String>, rng: Random, seasonNumber: Int): Map<String, Deadline> {
        val result = mutableMapOf<String, Deadline>()
        val allTypes = DeadlineType.entries
        for (artistId in artistIds.toList().sorted()) {
            val count = 1 + rng.nextInt(2)  // 1-2 deadlines per artist
            val shuffledTypes = allTypes.shuffled(rng)
            for (i in 0 until count) {
                val type = shuffledTypes[i]
                val id = "deadline:${artistId}:${type.name}:$seasonNumber"
                // 60-160 ticks: spread across season, biased away from the very start and end.
                val dueTick = 60 + rng.nextInt(101)
                result[id] = Deadline(id = id, artistId = artistId, type = type, dueTick = dueTick)
            }
        }
        return result
    }

    private fun buildScout(id: String, index: Int, rng: Random): ScoutState {
        val focusCount = 1 + rng.nextInt(2) // 1 or 2 focus genres
        val focusGenres = (0 until focusCount).map { GENRES.random(rng) }.toSet()
        return ScoutState(
            id = id,
            name = "${SCOUT_FIRST.random(rng)} ${SCOUT_LAST.random(rng)}",
            focusGenres = focusGenres,
            lastReportDay = -(index * (SCOUT_REPORT_INTERVAL / 2))
        )
    }
}
