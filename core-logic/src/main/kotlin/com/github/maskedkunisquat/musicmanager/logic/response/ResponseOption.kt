package com.github.maskedkunisquat.musicmanager.logic.response

import com.github.maskedkunisquat.musicmanager.logic.model.NeedType

data class ResponseOption(
    val id: String,
    val text: String,
    val effects: List<StateEffect>,
    val costFunds: Long = 0L
)

sealed class StateEffect {
    data class NeedChange(
        val artistId: String,
        val needType: NeedType,
        val delta: Float
    ) : StateEffect()

    data class LabelFundsChange(val delta: Long) : StateEffect()

    data class RelationshipChange(
        val artistId: String,
        val delta: Float
    ) : StateEffect()
}
