package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class ScoutState(
    val id: String,
    val name: String,
    val focusGenres: Set<String>,
    val lastReportDay: Int = 0
)
