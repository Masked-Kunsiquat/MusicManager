package com.github.maskedkunisquat.musicmanager.logic.model

data class Contract(
    val id: String,
    val artistId: String,
    val startDay: Int,
    val expiryDay: Int,
    val revenueSplit: RevenueSplit,
    val creativeControl: CreativeControl
)

data class RevenueSplit(val artistPercent: Int)

enum class CreativeControl { FULL_ARTIST, SHARED, FULL_LABEL }
