package com.iponlove.app.core.network

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.remote.TransactionDto
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.serialization.encodeToString
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Guards the fix for the mixed-batch NULL-constraint sync bug: supabase-kt's default JSON
 * serializer sets `encodeDefaults = false`, which drops any DTO field left at its Kotlin
 * default from that row's JSON. In a batch upsert Postgrest then writes literal NULL for the
 * omitted column (bypassing its `default`), so a batch mixing e.g. settlement and
 * non-settlement transactions violates `transactions.is_settlement`'s `not null`.
 * [iponPostgrestJson] flips `encodeDefaults = true`; these tests pin that invariant.
 */
class IponPostgrestJsonTest {

    private fun txn(isSettlement: Boolean) = TransactionDto(
        id = "t1",
        userId = "u1",
        type = TransactionType.EXPENSE,
        amount = BigDecimal("100.00"),
        accountId = "a1",
        toAccountId = null,
        categoryId = "c1",
        note = null,
        date = Instant.parse("2026-07-14T00:00:00Z"),
        isPrivate = false,
        recurringRuleId = null,
        isSettlement = isSettlement,
        createdAt = Instant.parse("2026-07-14T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-14T00:00:00Z"),
        isDeleted = false,
        serverRev = null,
    )

    @Test
    fun `config encodes defaults`() {
        // The load-bearing invariant for every defaulted not-null column across all DTOs
        // (is_settlement, is_premium, entitlement_source, is_paused, rollover_enabled,
        // is_pinned, is_conflict_copy, is_netting). If anyone flips this back to false the
        // mixed-batch bug returns for all of them.
        assertThat(iponPostgrestJson.configuration.encodeDefaults).isTrue()
    }

    @Test
    fun `defaulted is_settlement is present in the wire JSON`() {
        // isSettlement = false is the Kotlin default — the exact case that used to vanish.
        val json = iponPostgrestJson.encodeToString(txn(isSettlement = false))
        assertThat(json).contains("\"is_settlement\":false")
    }

    @Test
    fun `true is_settlement is present too so a mixed batch has uniform columns`() {
        val json = iponPostgrestJson.encodeToString(txn(isSettlement = true))
        assertThat(json).contains("\"is_settlement\":true")
    }
}
