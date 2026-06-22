package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class RivalState(
    val id: String,
    val name: String,
    // Higher weight = rival prioritizes this genre when targeting prospects and artists.
    val genreWeights: Map<String, Float>
)
