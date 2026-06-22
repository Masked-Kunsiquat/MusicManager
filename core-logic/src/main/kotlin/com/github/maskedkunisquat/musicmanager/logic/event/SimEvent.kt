package com.github.maskedkunisquat.musicmanager.logic.event

import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
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

    data class RenewalOpened(
        override val artistId: String,
        val contractId: String,
        val round: Int,
        override val dayOfGame: Int
    ) : SimEvent()

    data class LabelNeedUrgent(
        val needType: LabelNeedType,
        val severity: Float,
        override val dayOfGame: Int
    ) : SimEvent()

    data class CapabilityUnlockable(
        val type: CapabilityType,
        val costFunds: Long,
        override val dayOfGame: Int
    ) : SimEvent()

    // Rival signed a prospect from the unsigned pool.
    // wasPlayerTarget = true if the prospect was in world.activeNegotiations when poached.
    data class RivalSigning(
        val rivalId: String,
        val rivalName: String,
        val prospectName: String,
        val genre: String,
        val wasPlayerTarget: Boolean,
        override val dayOfGame: Int
    ) : SimEvent()

    // A prospect tape was surfaced for the player to review in the tape deck.
    // Not an inbox email -- resolved via TapeDeckScreen PURSUE/PASS/WATCH buttons.
    data class LeadSurfaced(
        val prospectId: String,
        override val dayOfGame: Int
    ) : SimEvent()

    // Rival poached a signed artist. Artist is already removed from world when this arrives.
    // artistName embedded because the world no longer contains the artist at render time.
    data class RivalPoach(
        val rivalId: String,
        val rivalName: String,
        override val artistId: String,
        val artistName: String,
        override val dayOfGame: Int
    ) : SimEvent()

    // Emitted at exactly 20, 10, and 5 ticks before dueTick — one event per threshold.
    data class DeadlineApproaching(
        val deadlineId: String,
        override val artistId: String,
        val type: DeadlineType,
        val ticksRemaining: Int,
        override val dayOfGame: Int
    ) : SimEvent()

    // Emitted the tick after dueTick when status is still PENDING.
    // SimEngine immediately sets status = MISSED to prevent re-emission.
    data class DeadlineMissed(
        val deadlineId: String,
        override val artistId: String,
        val type: DeadlineType,
        override val dayOfGame: Int
    ) : SimEvent()

    // Emitted once when currentDay >= seasonEndTick. Not an inbox item —
    // surfaced via observeUnresolvedSeasonEnd() DAO query.
    data class SeasonEnded(
        val seasonNumber: Int,
        override val dayOfGame: Int
    ) : SimEvent()
}
