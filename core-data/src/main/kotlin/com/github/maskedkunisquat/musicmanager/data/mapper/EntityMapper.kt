package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val entityJson = Json { ignoreUnknownKeys = true }

fun EventLogEntity.toInboxItemOrNull(): InboxItem? {
    val event = toSimEventOrNull() ?: return null
    val options = optionsJson?.let { json ->
        runCatching {
            entityJson.decodeFromString(ListSerializer(ResponseOption.serializer()), json)
        }.getOrNull()
    } ?: emptyList()
    val email = GeneratedEmail(subject = emailSubject, body = emailBody, options = options)
    return InboxItem(id = id, event = event, email = email, dayOfGame = dayOfGame)
}

fun EventLogEntity.toSimEventOrNull(): SimEvent? = try {
    val json = Json.parseToJsonElement(payload).jsonObject
    when (eventType) {
        "need_urgent" -> SimEvent.NeedUrgent(
            artistId = json["artistId"]!!.jsonPrimitive.content,
            needType = NeedType.valueOf(json["needType"]!!.jsonPrimitive.content),
            currentValue = json["currentValue"]!!.jsonPrimitive.content.toFloat(),
            dayOfGame = dayOfGame
        )
        "contract_expiring" -> SimEvent.ContractExpiring(
            artistId = json["artistId"]!!.jsonPrimitive.content,
            contractId = json["contractId"]!!.jsonPrimitive.content,
            daysRemaining = json["daysRemaining"]!!.jsonPrimitive.int,
            dayOfGame = dayOfGame
        )
        "want_surfaced" -> SimEvent.WantSurfaced(
            artistId = json["artistId"]!!.jsonPrimitive.content,
            wantType = WantType.valueOf(json["wantType"]!!.jsonPrimitive.content),
            urgency = json["urgency"]!!.jsonPrimitive.content.toFloat(),
            dayOfGame = dayOfGame
        )
        else -> null
    }
} catch (_: Exception) {
    null // corrupt entity — skip rather than crash
}
