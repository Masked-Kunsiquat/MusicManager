package com.github.maskedkunisquat.musicmanager.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class DemoState(
    val descriptor: String,   // 2–3 word vibe label shown in the tape deck
    val rawScore: Float,       // 0–1, hidden from player; updated on WatchLead
    val submittedDay: Int
)
