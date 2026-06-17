package com.github.maskedkunisquat.musicmanager.logic.event

import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType

sealed class SimEvent {
    abstract val artistId: String
    abstract val dayOfGame: Int

    data class NeedUrgent(
        override val artistId: String,
        val needType: NeedType,
        val currentValue: Float,
        override val dayOfGame: Int
    ) : SimEvent()

    data class ContractExpiring(
        override val artistId: String,
        val contractId: String,
        val daysRemaining: Int,
        override val dayOfGame: Int
    ) : SimEvent()

    data class WantSurfaced(
        override val artistId: String,
        val wantType: WantType,
        val urgency: Float,
        override val dayOfGame: Int
    ) : SimEvent()
}
