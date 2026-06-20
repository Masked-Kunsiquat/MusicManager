package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
enum class WantType {
    MAJOR_VENUE_TOUR,
    COLLAB_WITH_PRODUCER,
    GENRE_EXPERIMENT,
    RECORD_ALBUM,
    INCREASED_ROYALTIES
}

@Serializable
data class Want(
    val type: WantType,
    val urgency: Float,     // 0f..1f
    val expiryDay: Int?
) {
    init {
        require(urgency in 0f..1f) { "urgency must be in 0f..1f, was $urgency" }
    }
}
