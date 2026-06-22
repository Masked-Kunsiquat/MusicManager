package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class SeasonState(
    val seasonNumber: Int = 1,
    val seasonStartTick: Int = 0,
    val seasonEndTick: Int = 180   // 180 ticks ≈ 20 real days (same as contract length)
)
