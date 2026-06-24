package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.toDomain
import com.iponlove.app.feature.transactions.data.toDto
import com.iponlove.app.feature.transactions.data.toEntity
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test

class TransactionMapperTest {

    @Test
    fun entityToDomain_keepsLedgerFields_andDropsSyncColumns() {
        val domain = transactionEntity(
            id = "t",
            type = TransactionType.TRANSFER,
            amount = "75.50",
            accountId = "acc-1",
            toAccountId = "acc-2",
            categoryId = null,
            isPrivate = true,
        ).toDomain()

        assertThat(domain.id).isEqualTo("t")
        assertThat(domain.type).isEqualTo(TransactionType.TRANSFER)
        assertThat(domain.amount.toPlainString()).isEqualTo("75.50")
        assertThat(domain.accountId).isEqualTo("acc-1")
        assertThat(domain.toAccountId).isEqualTo("acc-2")
        assertThat(domain.isPrivate).isTrue()
    }

    @Test
    fun entityToDto_carriesRecurringRuleAndServerRev_andOmitsPendingSync() {
        val dto = transactionEntity(id = "t", recurringRuleId = "rule-1", serverRev = 42, pendingSync = true)
            .toDto()

        assertThat(dto.recurringRuleId).isEqualTo("rule-1")
        assertThat(dto.serverRev).isEqualTo(42)
        // TransactionDto has no pendingSync field at all — it can never go over the wire.
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = transactionDto(id = "t", serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
    }

    @Test
    fun entityToDto_roundTrips() {
        val original = transactionEntity(id = "t", recurringRuleId = "rule-1", serverRev = 9)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }
}
