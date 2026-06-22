package com.github.maskedkunisquat.musicmanager.data.dao

import com.github.maskedkunisquat.musicmanager.data.entity.EventLogEntity
import java.security.MessageDigest

internal fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

// Pure function for testability. Expects rows sorted by recordedAt ASC.
// Pre-v6 rows have payloadHash == "" and are skipped; their prevHash slot
// resets to "" so the first hashed row after them anchors cleanly.
internal fun verifyChainOf(rows: List<EventLogEntity>): Boolean {
    var prevHash = ""
    for (row in rows) {
        if (row.payloadHash.isEmpty()) {
            prevHash = ""
            continue
        }
        if (row.prevHash != prevHash) return false
        if (sha256(row.payload) != row.payloadHash) return false
        prevHash = row.payloadHash
    }
    return true
}
