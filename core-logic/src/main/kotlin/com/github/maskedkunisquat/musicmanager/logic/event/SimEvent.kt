package com.github.maskedkunisquat.musicmanager.logic.event

import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType

sealed class SimEvent {
    // null for market events (MarketShift, IntelDrop, ScoutReport) that have no roster artist.
    open val artistId: String? = null
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

    data class MarketShift(
        val genre: String,
        val previousTrend: Float,
        val currentTrend: Float,
        override val dayOfGame: Int
    ) : SimEvent()

    data class IntelDrop(
        val genre: String,
        val headline: String,
        override val dayOfGame: Int
    ) : SimEvent()

    data class ScoutReport(
        val scoutId: String,
        val prospectId: String,
        override val dayOfGame: Int
    ) : SimEvent()

    data class NegotiationRound(
        val prospectId: String,
        val round: Int,
        override val dayOfGame: Int
    ) : SimEvent()
}
