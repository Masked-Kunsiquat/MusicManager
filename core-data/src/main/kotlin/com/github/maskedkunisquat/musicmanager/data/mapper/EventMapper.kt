package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

fun SimEvent.toEntity(): EventLogEntity = EventLogEntity(
    id = UUID.randomUUID().toString(),
    dayOfGame = dayOfGame,
    eventType = eventTypeKey(),
    payload = toPayloadJson(),
    recordedAt = System.currentTimeMillis()
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
        put("currentValue", "%.4f".format(currentValue))
    }
    is SimEvent.ContractExpiring -> buildJsonObject {
        put("artistId", artistId)
        put("contractId", contractId)
        put("daysRemaining", daysRemaining)
    }
    is SimEvent.WantSurfaced -> buildJsonObject {
        put("artistId", artistId)
        put("wantType", wantType.name)
        put("urgency", "%.4f".format(urgency))
    }
}.toString()
