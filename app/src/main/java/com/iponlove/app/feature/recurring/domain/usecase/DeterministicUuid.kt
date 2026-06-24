package com.iponlove.app.feature.recurring.domain.usecase

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * RFC 4122 name-based (version 5, SHA-1) UUIDs. Pure Kotlin/JDK — no Android imports.
 *
 * Recurring materialization derives each generated transaction's id from
 * `ruleId:occurrenceDate` (see [MaterializeRecurringRulesUseCase]). Because the id is a
 * deterministic function of the rule and date, two synced devices independently compute
 * the *same* id, so a sync upsert collapses the would-be duplicate into one row — and a
 * tombstoned occurrence is never resurrected. This is the idempotency the recurring
 * feature was gated on.
 *
 * v5 (SHA-1) rather than the JDK's built-in [UUID.nameUUIDFromBytes] (v3/MD5) — same
 * determinism, the modern hash.
 */
object DeterministicUuid {

    /** Fixed app namespace; names are stable within it across devices and installs. */
    private val NAMESPACE: UUID = UUID.fromString("9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f")

    fun v5(name: String): UUID {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(NAMESPACE.toBytes())
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val bytes = sha1.digest().copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte() // version 5
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // RFC 4122 variant
        return bytes.toUuid()
    }

    private fun UUID.toBytes(): ByteArray =
        ByteBuffer.allocate(16)
            .putLong(mostSignificantBits)
            .putLong(leastSignificantBits)
            .array()

    private fun ByteArray.toUuid(): UUID {
        val bb = ByteBuffer.wrap(this)
        return UUID(bb.long, bb.long)
    }
}
