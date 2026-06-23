package com.github.maskedkunisquat.musicmanager.logic.sim

import com.github.maskedkunisquat.musicmanager.logic.model.ArtistState
import com.github.maskedkunisquat.musicmanager.logic.model.GenreAction
import com.github.maskedkunisquat.musicmanager.logic.model.LabelAesthetic
import com.github.maskedkunisquat.musicmanager.logic.model.LabelIdentity
import com.github.maskedkunisquat.musicmanager.logic.model.SimWorld
import kotlin.math.ln
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Minimal record of entity fields needed to extract genre actions from stored event log rows.
// Lets extractGenreActions live in core-logic while entity-to-domain mapping stays in core-data.
data class EntityRecord(val id: String, val eventType: String, val payload: String)

// 0.5f is the neutral baseline; each artist adds 0.06f, so ~8 artists of the same genre saturate at 1.0f.
private const val ROSTER_WEIGHT_PER_ARTIST = 0.06f

private val json = Json { ignoreUnknownKeys = true }

object LabelIdentityEvaluator {

    fun evaluate(actions: List<GenreAction>, artists: Collection<ArtistState>): LabelIdentity {
        val weights = mutableMapOf<String, Float>()
        // Seed from roster: each signed artist anchors their genre above the neutral baseline.
        // This means a player who hasn't scouted yet still has a readable identity from their roster.
        for (artist in artists) {
            val current = weights.getOrDefault(artist.genre, 0.5f)
            weights[artist.genre] = (current + ROSTER_WEIGHT_PER_ARTIST).coerceIn(0f, 1f)
        }
        // Fold player actions on top; scouting signals dominate over the roster baseline.
        for (action in actions) {
            val current = weights.getOrDefault(action.genre, 0.5f)
            weights[action.genre] = (current + action.delta).coerceIn(0f, 1f)
        }
        val primaryGenre = weights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value > 0f }?.key
        return LabelIdentity(
            genreWeights = weights.toMap(),
            primaryGenre = primaryGenre,
            focusScore = computeFocusScore(weights),
            aesthetic = deriveAesthetic(artists)
        )
    }

    // Extracts GenreAction list from season entities.
    // Sources: scouting decisions (pursue/pass/sign) + market and intel responses.
    // Genre is resolved from the original event for market/intel; from world lookup for scouting.
    fun extractGenreActions(entities: List<EntityRecord>, world: SimWorld): List<GenreAction> {
        // Index genre by event ID for any market_shift or intel_drop event in this season.
        // response_applied rows reference these via originalEventId.
        val genreByEventId = buildMap<String, String> {
            for (entity in entities) {
                if (entity.eventType != "market_shift" && entity.eventType != "intel_drop") continue
                runCatching {
                    val genre = json.parseToJsonElement(entity.payload)
                        .jsonObject["genre"]?.jsonPrimitive?.content ?: return@runCatching
                    put(entity.id, genre)
                }
            }
        }

        val result = mutableListOf<GenreAction>()
        for (entity in entities) {
            if (entity.eventType != "response_applied") continue
            runCatching {
                val payload = json.parseToJsonElement(entity.payload).jsonObject
                val effects = payload["effects"]?.jsonArray ?: return@runCatching

                val originalEventId = payload["originalEventId"]?.jsonPrimitive?.content
                originalEventId?.let { genreByEventId[it] }
                    ?.let { result += GenreAction(it, +0.03f) }

                for (e in effects) {
                    val obj = e.jsonObject
                    val action: GenreAction? = when (obj["type"]?.jsonPrimitive?.content) {
                        "pursue_lead" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveProspectGenre(it, world) }
                            ?.let { GenreAction(it, +0.05f) }
                        "pass_lead" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveProspectGenre(it, world) }
                            ?.let { GenreAction(it, -0.03f) }
                        "sign_artist" -> obj["prospectId"]?.jsonPrimitive?.content
                            ?.let { resolveSignedGenre(it, world) }
                            ?.let { GenreAction(it, +0.10f) }
                        else -> null
                    }
                    action?.let { result += it }
                }
            }
        }
        return result
    }

    // Normalized inverse Shannon entropy over the weight distribution.
    // 0 = weights are evenly spread (no focus), 1 = one genre holds all weight.
    // Empty map → 0f (no identity established yet).
    internal fun computeFocusScore(weights: Map<String, Float>): Float {
        if (weights.isEmpty()) return 0f
        val total = weights.values.sum()
        if (total <= 0f) return 0f
        val entropy = weights.values.fold(0.0) { acc, w ->
            val p = (w / total).toDouble()
            if (p <= 0.0) acc else acc - p * ln(p)
        }.toFloat()
        val maxEntropy = ln(weights.size.toDouble()).toFloat()
        return if (maxEntropy <= 0f) 1f else (1f - entropy / maxEntropy).coerceIn(0f, 1f)
    }

    // Aesthetic is driven by roster composition, not genre weights.
    // Priority: EXPERIMENTAL > MAINSTREAM > UNDERGROUND > ECLECTIC.
    internal fun deriveAesthetic(artists: Collection<ArtistState>): LabelAesthetic {
        if (artists.isEmpty()) return LabelAesthetic.ECLECTIC
        val n = artists.size
        val highVolatility = artists.count { it.dimensions.volatility >= 0.65f }
        if (highVolatility * 2 > n) return LabelAesthetic.EXPERIMENTAL
        val highCommercial = artists.count { it.dimensions.commercialAppetite >= 0.65f }
        if (highCommercial * 2 > n) return LabelAesthetic.MAINSTREAM
        val topGenreCount = artists.groupingBy { it.genre }.eachCount().values.maxOrNull() ?: 0
        if (topGenreCount.toFloat() / n > 0.70f) return LabelAesthetic.UNDERGROUND
        return LabelAesthetic.ECLECTIC
    }

    private fun resolveProspectGenre(prospectId: String, world: SimWorld): String? =
        world.prospects[prospectId]?.genre
            ?: world.artists["$SIGNED_ARTIST_ID_PREFIX$prospectId"]?.genre

    private fun resolveSignedGenre(prospectId: String, world: SimWorld): String? =
        world.artists["$SIGNED_ARTIST_ID_PREFIX$prospectId"]?.genre
            ?: world.prospects[prospectId]?.genre
}
