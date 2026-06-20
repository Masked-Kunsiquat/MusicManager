package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.ReputationCommunity
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.data.db.worldJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID

const val EVENT_TYPE_INTEL_DROP = "intel_drop"
const val EVENT_TYPE_RIVAL_SIGNING = "rival_signing"
const val EVENT_TYPE_RIVAL_POACH = "rival_poach"

// Stable identity key for deduplication — one unresolved event per (artist, need/want type),
// per contract, per genre shift, or per scout/prospect pair.
fun SimEvent.eventSignature(): String = when (this) {
    is SimEvent.NeedUrgent -> "need_urgent:$artistId:${needType.name}"
    is SimEvent.ContractExpiring -> "contract_expiring:$contractId"
    is SimEvent.WantSurfaced -> "want_surfaced:$artistId:${wantType.name}"
    is SimEvent.MarketShift -> "market_shift:$genre:$dayOfGame"
    is SimEvent.IntelDrop -> "intel_drop:$genre:$dayOfGame"
    is SimEvent.ScoutReport -> "scout_report:$scoutId:$prospectId"
    is SimEvent.NegotiationRound -> "negotiation_round:$prospectId:$round"
    is SimEvent.RenewalOpened -> "renewal_opened:$artistId:$round"
    is SimEvent.LabelNeedUrgent -> "label_need_urgent:${needType.name}"
    is SimEvent.CapabilityUnlockable -> "capability_unlockable:${type.name}"
    is SimEvent.RivalSigning -> "rival_signing:$rivalId:$prospectName"
    is SimEvent.RivalPoach -> "rival_poach:$rivalId:$artistId"
}

fun SimEvent.toEntity(email: GeneratedEmail): EventLogEntity = EventLogEntity(
    id = UUID.randomUUID().toString(),
    dayOfGame = dayOfGame,
    eventType = eventTypeKey(),
    payload = toPayloadJson(),
    recordedAt = System.currentTimeMillis(),
    emailSubject = email.subject,
    emailBody = email.body,
    optionsJson = if (email.options.isEmpty()) null else worldJson.encodeToString(email.options),
    viewedAt = null,
    selectedOptionId = null,
    resolvedAt = null
)

fun ResponseOption.toResponseEntity(originalEventId: String, dayOfGame: Int): EventLogEntity {
    val now = System.currentTimeMillis()
    val payload = buildJsonObject {
        put("originalEventId", originalEventId)
        put("optionId", id)
        put("optionText", text)
        put("costFunds", costFunds)
        put("effects", buildJsonArray {
            effects.forEach { effect ->
                add(buildJsonObject {
                    when (effect) {
                        is StateEffect.NeedChange -> {
                            put("type", "need_change")
                            put("artistId", effect.artistId)
                            put("needType", effect.needType.name)
                            put("delta", String.format(Locale.US, "%.4f", effect.delta))
                        }
                        is StateEffect.LabelFundsChange -> {
                            put("type", "funds_change")
                            put("delta", effect.delta)
                        }
                        is StateEffect.RelationshipChange -> {
                            put("type", "relationship_change")
                            put("artistId", effect.artistId)
                            put("delta", String.format(Locale.US, "%.4f", effect.delta))
                        }
                        is StateEffect.RosterNeedChange -> {
                            put("type", "roster_need_change")
                            put("needType", effect.needType.name)
                            put("delta", String.format(Locale.US, "%.4f", effect.delta))
                        }
                        is StateEffect.PairedNeedChange -> {
                            put("type", "paired_need_change")
                            put("partnerId", effect.partnerId)
                            put("needType", effect.needType.name)
                            put("delta", String.format(Locale.US, "%.4f", effect.delta))
                        }
                        is StateEffect.AdvanceNegotiation -> {
                            put("type", "advance_negotiation")
                            put("prospectId", effect.prospectId)
                        }
                        is StateEffect.SignArtist -> {
                            put("type", "sign_artist")
                            put("prospectId", effect.prospectId)
                        }
                        is StateEffect.NegotiationFailed -> {
                            put("type", "negotiation_failed")
                            put("prospectId", effect.prospectId)
                        }
                        is StateEffect.ReputationChange -> {
                            put("type", "reputation_change")
                            put("community", effect.community.name)
                            put("delta", String.format(Locale.US, "%.4f", effect.delta))
                        }
                        is StateEffect.UnlockCapability -> {
                            put("type", "unlock_capability")
                            put("capabilityType", effect.type.name)
                        }
                        is StateEffect.OpenRenewal -> {
                            put("type", "open_renewal")
                            put("artistId", effect.artistId)
                            put("contractId", effect.contractId)
                        }
                        is StateEffect.AdvanceRenewal -> {
                            put("type", "advance_renewal")
                            put("artistId", effect.artistId)
                            put("contractId", effect.contractId)
                        }
                        is StateEffect.RenewContract -> {
                            put("type", "renew_contract")
                            put("artistId", effect.artistId)
                            put("newExpiryTicks", effect.newExpiryTicks)
                            put("artistPercent", effect.revenueSplit.artistPercent)
                            put("creativeControl", effect.creativeControl.name)
                        }
                        is StateEffect.RenewalWalked -> {
                            put("type", "renewal_walked")
                            put("artistId", effect.artistId)
                        }
                        is StateEffect.WantSatisfied -> {
                            put("type", "want_satisfied")
                            put("artistId", effect.artistId)
                            put("wantType", effect.wantType.name)
                        }
                    }
                })
            }
        })
    }.toString()
    return EventLogEntity(
        id = UUID.randomUUID().toString(),
        dayOfGame = dayOfGame,
        eventType = "response_applied",
        payload = payload,
        recordedAt = now,
        emailSubject = "",
        emailBody = "",
        optionsJson = null,
        viewedAt = now,         // response_applied rows are never shown in the inbox
        selectedOptionId = id,  // pre-resolved — this event IS the decision, not a pending item
        resolvedAt = now
    )
}

private fun SimEvent.eventTypeKey(): String = when (this) {
    is SimEvent.NeedUrgent -> "need_urgent"
    is SimEvent.ContractExpiring -> "contract_expiring"
    is SimEvent.WantSurfaced -> "want_surfaced"
    is SimEvent.MarketShift -> "market_shift"
    is SimEvent.IntelDrop -> EVENT_TYPE_INTEL_DROP
    is SimEvent.ScoutReport -> "scout_report"
    is SimEvent.NegotiationRound -> "negotiation_round"
    is SimEvent.RenewalOpened -> "renewal_opened"
    is SimEvent.LabelNeedUrgent -> "label_need_urgent"
    is SimEvent.CapabilityUnlockable -> "capability_unlockable"
    is SimEvent.RivalSigning -> "rival_signing"
    is SimEvent.RivalPoach -> "rival_poach"
}

private fun SimEvent.toPayloadJson(): String = when (this) {
    is SimEvent.NeedUrgent -> buildJsonObject {
        put("artistId", artistId)
        put("needType", needType.name)
        put("currentValue", String.format(Locale.US, "%.4f", currentValue))
    }
    is SimEvent.ContractExpiring -> buildJsonObject {
        put("artistId", artistId)
        put("contractId", contractId)
        put("daysRemaining", daysRemaining)
    }
    is SimEvent.WantSurfaced -> buildJsonObject {
        put("artistId", artistId)
        put("wantType", wantType.name)
        put("urgency", String.format(Locale.US, "%.4f", urgency))
    }
    is SimEvent.MarketShift -> buildJsonObject {
        put("genre", genre)
        put("previousTrend", String.format(Locale.US, "%.4f", previousTrend))
        put("currentTrend", String.format(Locale.US, "%.4f", currentTrend))
    }
    is SimEvent.IntelDrop -> buildJsonObject {
        put("genre", genre)
        put("headline", headline)
    }
    is SimEvent.ScoutReport -> buildJsonObject {
        put("scoutId", scoutId)
        put("prospectId", prospectId)
    }
    is SimEvent.NegotiationRound -> buildJsonObject {
        put("prospectId", prospectId)
        put("round", round)
    }
    is SimEvent.RenewalOpened -> buildJsonObject {
        put("artistId", artistId)
        put("contractId", contractId)
        put("round", round)
    }
    is SimEvent.LabelNeedUrgent -> buildJsonObject {
        put("needType", needType.name)
        put("severity", String.format(Locale.US, "%.4f", severity))
    }
    is SimEvent.CapabilityUnlockable -> buildJsonObject {
        put("type", type.name)
        put("costFunds", costFunds)
    }
    is SimEvent.RivalSigning -> buildJsonObject {
        put("rivalId", rivalId)
        put("rivalName", rivalName)
        put("prospectName", prospectName)
        put("genre", genre)
        put("wasPlayerTarget", wasPlayerTarget)
    }
    is SimEvent.RivalPoach -> buildJsonObject {
        put("rivalId", rivalId)
        put("rivalName", rivalName)
        put("artistId", artistId)
        put("artistName", artistName)
    }
}.toString()
