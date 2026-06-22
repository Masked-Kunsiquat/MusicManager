package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

enum class DeadlineType { ALBUM_RELEASE, TOUR_BOOKING, PRESS_CYCLE }

enum class DeadlineStatus { PENDING, MET, MISSED, EXTENDED }

@Serializable
data class Deadline(
    val id: String,
    val artistId: String,
    val type: DeadlineType,
    val dueTick: Int,
    val status: DeadlineStatus = DeadlineStatus.PENDING
)
