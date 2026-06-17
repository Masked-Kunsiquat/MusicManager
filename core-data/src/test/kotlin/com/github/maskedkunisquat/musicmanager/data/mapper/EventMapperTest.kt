package com.github.maskedkunisquat.musicmanager.data.mapper

import com.github.maskedkunisquat.musicmanager.logic.event.SimEvent
import com.github.maskedkunisquat.musicmanager.logic.model.NeedType
import com.github.maskedkunisquat.musicmanager.logic.model.WantType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMapperTest {

    @Test
    fun `NeedUrgent maps to correct event type and day`() {
        val event = SimEvent.NeedUrgent("artist_1", NeedType.BELONGING, 0.25f, dayOfGame = 5)
        val entity = event.toEntity()
        assertEquals("need_urgent", entity.eventType)
        assertEquals(5, entity.dayOfGame)
    }

    @Test
    fun `NeedUrgent payload formats currentValue to 4 decimal places`() {
        val event = SimEvent.NeedUrgent("artist_1", NeedType.BELONGING, 0.3f, dayOfGame = 1)
        val payload = Json.parseToJsonElement(event.toEntity().payload).jsonObject
        // Without formatting, 0.3f.toDouble() → "0.30000001192092896"
        assertEquals("0.3000", payload["currentValue"]!!.jsonPrimitive.content)
        assertEquals("BELONGING", payload["needType"]!!.jsonPrimitive.content)
        assertEquals("artist_1", payload["artistId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ContractExpiring maps correctly`() {
        val event = SimEvent.ContractExpiring("artist_1", "contract_42", daysRemaining = 15, dayOfGame = 10)
        val entity = event.toEntity()
        assertEquals("contract_expiring", entity.eventType)
        assertEquals(10, entity.dayOfGame)
        val payload = Json.parseToJsonElement(entity.payload).jsonObject
        assertEquals("artist_1", payload["artistId"]!!.jsonPrimitive.content)
        assertEquals("contract_42", payload["contractId"]!!.jsonPrimitive.content)
        assertEquals(15, payload["daysRemaining"]!!.jsonPrimitive.int)
    }

    @Test
    fun `WantSurfaced payload formats urgency to 4 decimal places`() {
        val event = SimEvent.WantSurfaced("artist_2", WantType.RECORD_ALBUM, urgency = 0.8f, dayOfGame = 20)
        val entity = event.toEntity()
        assertEquals("want_surfaced", entity.eventType)
        assertEquals(20, entity.dayOfGame)
        val payload = Json.parseToJsonElement(entity.payload).jsonObject
        assertEquals("artist_2", payload["artistId"]!!.jsonPrimitive.content)
        assertEquals("RECORD_ALBUM", payload["wantType"]!!.jsonPrimitive.content)
        assertEquals("0.8000", payload["urgency"]!!.jsonPrimitive.content)
    }
}
