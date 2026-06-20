package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class Contract(
    val id: String,
    val artistId: String,
    val startDay: Int,
    val expiryDay: Int,
    val revenueSplit: RevenueSplit,
    val creativeControl: CreativeControl
) {
    init {
        require(expiryDay >= startDay) {
            "expiryDay ($expiryDay) must be >= startDay ($startDay)"
        }
    }
}

@Serializable
data class RevenueSplit(val artistPercent: Int) {
    init {
        require(artistPercent in 0..100) {
            "artistPercent must be in 0..100, was $artistPercent"
        }
    }
}

@Serializable
enum class CreativeControl { FULL_ARTIST, SHARED, FULL_LABEL }
