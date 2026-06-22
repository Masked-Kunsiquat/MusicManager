package com.github.maskedkunisquat.musicmanager.logic.rivalintel

import com.github.maskedkunisquat.musicmanager.logic.model.RivalSnapshot

/**
 * Display-time fuzz applied to [RivalSnapshot] values based on confidence.
 *
 * Confidence decays 0.05f per 10 ticks since [RivalSnapshot.snapshotDay].
 * Below 0.3f the entry is considered stale; below 0.0f it is clamped there.
 *
 * Fuzz is deterministic per snapshot so the UI is stable between recompositions.
 * It is NOT stored — always computed fresh from the persisted snapshot.
 */
object RivalIntelFuzzer {

    private const val DECAY_PER_10_TICKS = 0.05f
    private const val STALE_THRESHOLD = 0.3f

    /** Effective confidence after applying tick-based decay. */
    fun currentConfidence(snapshot: RivalSnapshot, currentDay: Int): Float {
        val ticksElapsed = (currentDay - snapshot.snapshotDay).coerceAtLeast(0)
        val decay = (ticksElapsed / 10) * DECAY_PER_10_TICKS
        return (snapshot.confidence - decay).coerceIn(0f, 1f)
    }

    fun isStale(snapshot: RivalSnapshot, currentDay: Int): Boolean =
        currentConfidence(snapshot, currentDay) < STALE_THRESHOLD

    /**
     * Returns a fuzzed roster size. Noise magnitude scales with (1 - confidence):
     * at confidence 1.0 the value is exact; at 0.0 it can be ± 2.
     */
    fun fuzzedRosterSize(snapshot: RivalSnapshot, currentDay: Int): Int {
        val conf = currentConfidence(snapshot, currentDay)
        val maxNoise = ((1f - conf) * 2f).toInt()  // 0 at conf=1.0, up to 2 at conf=0.0
        if (maxNoise == 0) return snapshot.observedRosterSize.coerceAtLeast(1)
        val noise = noiseSeed(snapshot.rivalId, snapshot.snapshotDay) % (maxNoise.toLong() + 1)
        val delta = if (noiseSeed(snapshot.rivalId, snapshot.snapshotDay + 1) % 2 == 0L) noise else -noise
        return (snapshot.observedRosterSize + delta).toInt().coerceAtLeast(1)
    }

    /**
     * Returns a fuzzed genre list. At low confidence one genre may be dropped or a
     * spurious entry added. The spurious genre is a deterministic placeholder string.
     */
    fun fuzzedGenres(snapshot: RivalSnapshot, currentDay: Int): List<String> {
        val conf = currentConfidence(snapshot, currentDay)
        if (conf >= 0.8f) return snapshot.observedGenres   // fresh — no fuzz
        val genres = snapshot.observedGenres.toMutableList()
        val seed = noiseSeed(snapshot.rivalId, snapshot.snapshotDay + 2)
        if (conf < 0.5f && genres.size > 1) {
            // Drop the last genre alphabetically (deterministic)
            genres.removeAt(genres.size - 1)
        }
        if (conf < 0.3f) {
            // Add a spurious genre so the player can't fully trust the list
            val spurious = SPURIOUS_GENRES[(seed % SPURIOUS_GENRES.size).toInt().coerceAtLeast(0)]
            if (spurious !in genres) genres.add(spurious)
        }
        return genres
    }

    // Deterministic noise seed from a string key + int offset — avoids Random state.
    private fun noiseSeed(key: String, offset: Int): Long =
        (key.hashCode().toLong() * 31L + offset) and Long.MAX_VALUE

    private val SPURIOUS_GENRES = listOf(
        "ambient", "bedroom-pop", "midwest-emo", "footwork", "neo-soul"
    )
}
