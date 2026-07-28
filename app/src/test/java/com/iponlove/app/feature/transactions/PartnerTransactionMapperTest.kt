package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.remote.PartnerTransactionDto
import com.iponlove.app.feature.transactions.data.toEntity
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

class PartnerTransactionMapperTest {

    @Test
    fun visibleRow_mapsContentAndOwnership_pendingSyncFalse() {
        val entity = partnerDto(
            id = "t", type = TransactionType.EXPENSE, amount = BigDecimal("250.00"),
            accountId = "acc-p", categoryId = "cat-p", isPrivate = false, isDeleted = false,
            serverRev = 12,
        ).toEntity()

        assertThat(entity.userId).isEqualTo("partner-1")
        assertThat(entity.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(entity.accountId).isEqualTo("acc-p")
        assertThat(entity.categoryId).isEqualTo("cat-p")
        assertThat(entity.serverRev).isEqualTo(12)
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun isAdjustment_carriedThroughToEntity() {
        val entity = partnerDto(
            id = "t", type = TransactionType.INCOME, amount = BigDecimal("500.00"),
            accountId = "acc-p", categoryId = null, isPrivate = false, isDeleted = false,
            serverRev = 1, isAdjustment = true,
        ).toEntity()

        assertThat(entity.isAdjustment).isTrue()
    }

    @Test
    fun redactedRow_nullContent_mapsToSafeDefaults_withoutCrashing() {
        // A private/deleted partner txn arrives with content nulled (ADR-0005); the syncer
        // purges it, but the mapper must still total the row without throwing.
        val entity = partnerDto(
            id = "t", type = null, amount = null, accountId = null, categoryId = null,
            isPrivate = true, isDeleted = false, serverRev = 5,
        ).toEntity()

        assertThat(entity.amount).isEqualTo(BigDecimal.ZERO)
        assertThat(entity.accountId).isEqualTo("")
        assertThat(entity.categoryId).isNull()
        assertThat(entity.isPrivate).isTrue()
        assertThat(entity.date).isEqualTo(Instant.ofEpochMilli(2_000)) // falls back to updatedAt
    }
}

private fun partnerDto(
    id: String,
    type: TransactionType?,
    amount: BigDecimal?,
    accountId: String?,
    categoryId: String?,
    isPrivate: Boolean,
    isDeleted: Boolean,
    serverRev: Long?,
    isAdjustment: Boolean = false,
) = PartnerTransactionDto(
    id = id,
    userId = "partner-1",
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = null,
    categoryId = categoryId,
    note = null,
    date = if (type == null) null else Instant.ofEpochMilli(1_000),
    isAdjustment = isAdjustment,
    isPrivate = isPrivate,
    isDeleted = isDeleted,
    updatedAt = Instant.ofEpochMilli(2_000),
    serverRev = serverRev,
)
