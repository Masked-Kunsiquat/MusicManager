package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import com.github.maskedkunisquat.musicmanager.data.db.worldJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID

// Stable identity key for deduplication — one unresolved event per (artist, need/want type)
// or per contract. Used in tickUnderLock to skip events already in the inbox.
fun SimEvent.eventSignature(): String = when (this) {
    is SimEvent.NeedUrgent -> "need_urgent:$artistId:${needType.name}"
    is SimEvent.ContractExpiring -> "contract_expiring:$contractId"
    is SimEvent.WantSurfaced -> "want_surfaced:$artistId:${wantType.name}"
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
}.toString()
