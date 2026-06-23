package com.github.maskedkunisquat.musicmanager.logic.model

data class LabelIdentity(
    // 0–1 per genre; starts at 0.5 neutral for any genre touched in the current season.
    val genreWeights: Map<String, Float>,
    // Highest-weight genre; null when no player actions have occurred this season.
    val primaryGenre: String?,
    // 0 = even spread across genres (scattered), 1 = single genre dominates.
    // Derived from normalized inverse Shannon entropy of genreWeights.
    val focusScore: Float,
    // Character of the roster derived from artist dimensions and genre concentration.
    val aesthetic: LabelAesthetic
)
