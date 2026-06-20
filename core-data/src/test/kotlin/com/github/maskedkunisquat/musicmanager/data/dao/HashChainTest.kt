package com.github.maskedkunisquat.musicmanager.data.dao

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashChainTest {

    private fun stubEntity(
        payload: String,
        prevHash: String = "",
        payloadHash: String = ""
    ) = EventLogEntity(
        id = "id_${System.nanoTime()}",
        dayOfGame = 1,
        eventType = "need_urgent",
        payload = payload,
        recordedAt = System.nanoTime(),
        emailSubject = "subject",
        emailBody = "body",
        optionsJson = null,
        viewedAt = null,
        selectedOptionId = null,
        resolvedAt = null,
        prevHash = prevHash,
        payloadHash = payloadHash
    )

    private fun hashedRow(payload: String, prev: String): EventLogEntity {
        val hash = sha256(payload)
        return stubEntity(payload, prevHash = prev, payloadHash = hash)
    }

    // --- sha256 sanity ---

    @Test
    fun `sha256 produces a 64-character hex string`() {
        val result = sha256("hello")
        assertEquals(64, result.length)
        assertTrue("Expected only hex chars", result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256 is deterministic`() {
        assertEquals(sha256("payload"), sha256("payload"))
    }

    @Test
    fun `sha256 differs for different inputs`() {
        assertNotEquals(sha256("a"), sha256("b"))
    }

    // --- verifyChainOf ---

    @Test
    fun `empty chain verifies`() {
        assertTrue(verifyChainOf(emptyList()))
    }

    @Test
    fun `genesis row with correct hash and empty prevHash verifies`() {
        val row = hashedRow("{}", prev = "")
        assertTrue(verifyChainOf(listOf(row)))
    }

    @Test
    fun `two-row chain with correct hashes verifies`() {
        val r1 = hashedRow("""{"a":1}""", prev = "")
        val r2 = hashedRow("""{"b":2}""", prev = r1.payloadHash)
        assertTrue(verifyChainOf(listOf(r1, r2)))
    }

    @Test
    fun `mutated payload breaks verification`() {
        val r1 = hashedRow("""{"a":1}""", prev = "")
        val r2 = hashedRow("""{"b":2}""", prev = r1.payloadHash)
        // Simulate a direct DB edit: payload changed but hash not updated.
        val tampered = r2.copy(payload = """{"b":99}""")
        assertFalse(verifyChainOf(listOf(r1, tampered)))
    }

    @Test
    fun `broken prevHash link breaks verification`() {
        val r1 = hashedRow("""{"a":1}""", prev = "")
        val r2 = hashedRow("""{"b":2}""", prev = "wrong_hash")
        assertFalse(verifyChainOf(listOf(r1, r2)))
    }

    @Test
    fun `pre-migration rows with empty hashes pass verification`() {
        val legacy1 = stubEntity("""{"old":1}""", prevHash = "", payloadHash = "")
        val legacy2 = stubEntity("""{"old":2}""", prevHash = "", payloadHash = "")
        assertTrue("Empty-hash legacy rows must be treated as internally consistent",
            verifyChainOf(listOf(legacy1, legacy2)))
    }

    @Test
    fun `first hashed row after legacy rows anchors cleanly`() {
        val legacy = stubEntity("""{"old":1}""", prevHash = "", payloadHash = "")
        val fresh = hashedRow("""{"new":1}""", prev = "")
        assertTrue(verifyChainOf(listOf(legacy, fresh)))
    }

    @Test
    fun `three-row chain breaks at middle link`() {
        val r1 = hashedRow("""{"a":1}""", prev = "")
        val r2 = hashedRow("""{"b":2}""", prev = r1.payloadHash)
        val r3 = hashedRow("""{"c":3}""", prev = "stale_hash")  // wrong prevHash
        assertFalse(verifyChainOf(listOf(r1, r2, r3)))
    }
}
