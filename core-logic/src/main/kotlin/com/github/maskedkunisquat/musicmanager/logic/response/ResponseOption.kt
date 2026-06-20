package com.github.maskedkunisquat.musicmanager.logic.response

import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.CreativeControl
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.model.RevenueSplit
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

    // Phase 2A: partnerId is filled at option-generation time (random roster pick).
    // Phase 2B: UI intercepts options containing this effect, shows a partner picker,
    // replaces partnerId with the player's choice before calling resolveEvent.
    @Serializable @SerialName("paired_need_change")
    data class PairedNeedChange(
        val partnerId: String,
        val needType: NeedType,
        val delta: Float
    ) : StateEffect()

    @Serializable @SerialName("advance_negotiation")
    data class AdvanceNegotiation(val prospectId: String) : StateEffect()

    @Serializable @SerialName("sign_artist")
    data class SignArtist(val prospectId: String) : StateEffect()

    @Serializable @SerialName("negotiation_failed")
    data class NegotiationFailed(val prospectId: String) : StateEffect()

    @Serializable @SerialName("reputation_change")
    data class ReputationChange(
        val community: ReputationCommunity,
        val delta: Float
    ) : StateEffect()

    @Serializable @SerialName("unlock_capability")
    data class UnlockCapability(val type: CapabilityType) : StateEffect()

    // Initiates renewal round 1 for the given artist+contract.
    @Serializable @SerialName("open_renewal")
    data class OpenRenewal(val artistId: String, val contractId: String) : StateEffect()

    // Advances to the next renewal round (max 3 total).
    @Serializable @SerialName("advance_renewal")
    data class AdvanceRenewal(val artistId: String, val contractId: String) : StateEffect()

    // Seals the renewal — creates a new contract from currentDay + newExpiryTicks.
    @Serializable @SerialName("renew_contract")
    data class RenewContract(
        val artistId: String,
        val newExpiryTicks: Int,
        val revenueSplit: RevenueSplit,
        val creativeControl: CreativeControl
    ) : StateEffect()

    // Artist walked from renewal talks. Loyalty -0.2f; accelerates rival poach if running.
    @Serializable @SerialName("renewal_walked")
    data class RenewalWalked(val artistId: String) : StateEffect()
}
