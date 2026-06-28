package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import com.github.maskedkunisquat.musicmanager.data.mapper.EVENT_TYPE_INTEL_DROP
import com.github.maskedkunisquat.musicmanager.logic.ai.GeneratedEmail
import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.inbox.InboxItem
import com.github.maskedkunisquat.musicmanager.logic.inbox.TapeDeckItem
import com.github.maskedkunisquat.musicmanager.logic.model.CapabilityType
import com.github.maskedkunisquat.musicmanager.logic.model.DeadlineType
import com.github.maskedkunisquat.musicmanager.logic.model.LabelNeedType
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import com.github.maskedkunisquat.musicmanager.data.db.worldJson
import com.github.maskedkunisquat.musicmanager.logic.response.ResponseOption
import com.github.maskedkunisquat.musicmanager.logic.response.StateEffect
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

fun EventLogEntity.toInboxItemOrNull(): InboxItem? {
    if (eventType == "lead_surfaced") return null  // TapeDeck only -- not an inbox email
    if (eventType == "season_ended") return null   // Season-end modal -- surfaced via observeUnresolvedSeasonEnd()
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
        "renewal_opened" -> SimEvent.RenewalOpened(
            artistId = json["artistId"]!!.jsonPrimitive.content,
            contractId = json["contractId"]!!.jsonPrimitive.content,
            round = json["round"]!!.jsonPrimitive.int,
            dayOfGame = dayOfGame
        )
        "label_need_urgent" -> SimEvent.LabelNeedUrgent(
            needType = LabelNeedType.valueOf(json["needType"]!!.jsonPrimitive.content),
            severity = json["severity"]!!.jsonPrimitive.content.toFloat(),
            dayOfGame = dayOfGame
        )
        "capability_unlockable" -> SimEvent.CapabilityUnlockable(
            type = CapabilityType.valueOf(json["type"]!!.jsonPrimitive.content),
            costFunds = json["costFunds"]!!.jsonPrimitive.long,
            dayOfGame = dayOfGame
        )
        "rival_signing" -> SimEvent.RivalSigning(
            rivalId = json["rivalId"]!!.jsonPrimitive.content,
            rivalName = json["rivalName"]!!.jsonPrimitive.content,
            prospectName = json["prospectName"]!!.jsonPrimitive.content,
            genre = json["genre"]!!.jsonPrimitive.content,
            wasPlayerTarget = json["wasPlayerTarget"]!!.jsonPrimitive.content.toBoolean(),
            dayOfGame = dayOfGame
        )
        "lead_surfaced" -> SimEvent.LeadSurfaced(
            prospectId = json["prospectId"]!!.jsonPrimitive.content,
            dayOfGame = dayOfGame
        )
        "rival_poach" -> SimEvent.RivalPoach(
            rivalId = json["rivalId"]!!.jsonPrimitive.content,
            rivalName = json["rivalName"]!!.jsonPrimitive.content,
            artistId = json["artistId"]!!.jsonPrimitive.content,
            artistName = json["artistName"]!!.jsonPrimitive.content,
            dayOfGame = dayOfGame
        )
        "deadline_approaching" -> SimEvent.DeadlineApproaching(
            deadlineId = json["deadlineId"]!!.jsonPrimitive.content,
            artistId = json["artistId"]!!.jsonPrimitive.content,
            type = DeadlineType.valueOf(json["type"]!!.jsonPrimitive.content),
            ticksRemaining = json["ticksRemaining"]!!.jsonPrimitive.int,
            dayOfGame = dayOfGame
        )
        "deadline_missed" -> SimEvent.DeadlineMissed(
            deadlineId = json["deadlineId"]!!.jsonPrimitive.content,
            artistId = json["artistId"]!!.jsonPrimitive.content,
            type = DeadlineType.valueOf(json["type"]!!.jsonPrimitive.content),
            dayOfGame = dayOfGame
        )
        "season_ended" -> SimEvent.SeasonEnded(
            seasonNumber = json["seasonNumber"]!!.jsonPrimitive.int,
            dayOfGame = dayOfGame
        )
        "check_in" -> SimEvent.CheckIn(
            artistId = json["artistId"]!!.jsonPrimitive.content,
            dayOfGame = dayOfGame
        )
        else -> null
    }
} catch (_: Exception) {
    null // corrupt entity — skip rather than crash
}

// Sums RelationshipChange deltas and WantSatisfied bonuses (StateEffect.WantSatisfied.RELATIONSHIP_BONUS
// each) per artist from a response_applied entity. Used to re-derive ArtistState.relationshipBalance
// on world load.
fun EventLogEntity.toRelationshipDeltas(): Map<String, Float> {
    if (eventType != "response_applied") return emptyMap()
    val result = mutableMapOf<String, Float>()
    runCatching {
        val effects = worldJson.parseToJsonElement(payload).jsonObject["effects"]?.jsonArray
            ?: return@runCatching
        for (e in effects) {
            val obj = e.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: continue
            val artistId = obj["artistId"]?.jsonPrimitive?.content ?: continue
            when (type) {
                "relationship_change" -> {
                    val delta = obj["delta"]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue
                    result[artistId] = (result[artistId] ?: 0f) + delta
                }
                "want_satisfied" -> result[artistId] = (result[artistId] ?: 0f) + StateEffect.WantSatisfied.RELATIONSHIP_BONUS
                // Read explicit delta stored since Phase 5-A; fall back to the historical
                // hard-coded values for rows written before that version.
                "renewal_walked" -> {
                    val delta = obj["delta"]?.jsonPrimitive?.content?.toFloatOrNull() ?: -0.2f
                    result[artistId] = (result[artistId] ?: 0f) + delta
                }
                "meet_deadline" -> {
                    val delta = obj["delta"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.05f
                    result[artistId] = (result[artistId] ?: 0f) + delta
                }
            }
        }
    }
    return result
}

// Returns the set of artist IDs directly touched by a response_applied entity.
// Used to back-fill ArtistState.lastInteractionDay for saves predating 4-D.
fun EventLogEntity.toTouchedArtistIds(): Set<String> {
    if (eventType != "response_applied") return emptySet()
    val result = mutableSetOf<String>()
    runCatching {
        val effects = worldJson.parseToJsonElement(payload).jsonObject["effects"]?.jsonArray
            ?: return@runCatching
        for (e in effects) {
            val obj = e.jsonObject
            (obj["artistId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: obj["partnerId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
                ?.let { result.add(it) }
        }
    }
    return result
}

fun EventLogEntity.toTapeDeckItemOrNull(): TapeDeckItem? {
    if (eventType != "lead_surfaced") return null
    val json = runCatching { worldJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    val prospectId = json["prospectId"]?.jsonPrimitive?.content ?: return null
    val options = optionsJson?.let { raw ->
        runCatching { worldJson.decodeFromString(ListSerializer(ResponseOption.serializer()), raw) }.getOrNull()
    } ?: emptyList()
    return TapeDeckItem(id = id, prospectId = prospectId, dayOfGame = dayOfGame, options = options)
}