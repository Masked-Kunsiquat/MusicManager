package com.github.maskedkunisquat.musicmanager.logic.model

enum class WantType {
    MAJOR_VENUE_TOUR,
    COLLAB_WITH_PRODUCER,
    GENRE_EXPERIMENT,
    RECORD_ALBUM,
    INCREASED_ROYALTIES
}

data class Want(
    val type: WantType,
    val urgency: Float,     // 0f..1f
    val expiryDay: Int?
)
