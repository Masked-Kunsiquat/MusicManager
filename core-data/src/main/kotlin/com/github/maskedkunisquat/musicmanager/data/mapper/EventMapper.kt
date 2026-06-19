package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
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
    selectedOptionId = null,
    resolvedAt = null
)

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
