package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.data.mapper.EVENT_TYPE_INTEL_DROP
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.data.db.worldJson
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun EventLogEntity.toInboxItemOrNull(): InboxItem? {
    val event = toSimEventOrNull() ?: return null
    val options = optionsJson?.let { json ->
        runCatching {
            worldJson.decodeFromString(ListSerializer(ResponseOption.serializer()), json)
        }.getOrNull()
    } ?: emptyList()
    val email = GeneratedEmail(subject = emailSubject, body = emailBody, options = options)
    return InboxItem(id = id, event = event, email = email, dayOfGame = dayOfGame, isRead = viewedAt != null)
}

fun EventLogEntity.toSimEventOrNull(): SimEvent? = try {
    val json = worldJson.parseToJsonElement(payload).jsonObject
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
        "market_shift" -> SimEvent.MarketShift(
            genre = json["genre"]!!.jsonPrimitive.content,
            previousTrend = json["previousTrend"]!!.jsonPrimitive.content.toFloat(),
            currentTrend = json["currentTrend"]!!.jsonPrimitive.content.toFloat(),
            dayOfGame = dayOfGame
        )
        EVENT_TYPE_INTEL_DROP -> SimEvent.IntelDrop(
            genre = json["genre"]!!.jsonPrimitive.content,
            headline = json["headline"]!!.jsonPrimitive.content,
            dayOfGame = dayOfGame
        )
        "scout_report" -> SimEvent.ScoutReport(
            scoutId = json["scoutId"]!!.jsonPrimitive.content,
            prospectId = json["prospectId"]!!.jsonPrimitive.content,
            dayOfGame = dayOfGame
        )
        "negotiation_round" -> SimEvent.NegotiationRound(
            prospectId = json["prospectId"]!!.jsonPrimitive.content,
            round = json["round"]!!.jsonPrimitive.int,
            dayOfGame = dayOfGame
        )
        else -> null
    }
} catch (_: Exception) {
    null // corrupt entity — skip rather than crash
}
