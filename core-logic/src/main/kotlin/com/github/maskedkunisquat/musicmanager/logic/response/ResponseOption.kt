package com.github.maskedkunisquat.musicmanager.logic.response

import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponseOption(
    val id: String,
    val text: String,
    val effects: List<StateEffect>,
    val costFunds: Long = 0L
)

@Serializable
sealed class StateEffect {
    @Serializable @SerialName("need_change")
    data class NeedChange(
        val artistId: String,
        val needType: NeedType,
        val delta: Float
    ) : StateEffect()

    @Serializable @SerialName("funds_change")
    data class LabelFundsChange(val delta: Long) : StateEffect()

    @Serializable @SerialName("relationship_change")
    data class RelationshipChange(
        val artistId: String,
        val delta: Float
    ) : StateEffect()

    @Serializable @SerialName("roster_need_change")
    data class RosterNeedChange(
        val needType: NeedType,
        val delta: Float
    ) : StateEffect()
}
