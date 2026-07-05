package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.data.toDomain
import com.iponlove.app.feature.budgets.data.toDto
import com.iponlove.app.feature.budgets.data.toEntity
import org.junit.Test

class BudgetMapperTest {

    @Test
    fun entityToDomain_dropsOwnershipAndSyncColumns() {
        val domain = budgetEntity(id = "b", categoryId = null, amount = "3000.00", yearMonth = "2026-07")
            .toDomain()

        assertThat(domain.id).isEqualTo("b")
        assertThat(domain.categoryId).isNull()
        assertThat(domain.amount.toPlainString()).isEqualTo("3000.00")
        assertThat(domain.yearMonth).isEqualTo("2026-07")
    }

    @Test
    fun entityToDomain_carriesRolloverEnabled() {
        val domain = budgetEntity(id = "b", rolloverEnabled = true).toDomain()

        assertThat(domain.rolloverEnabled).isTrue()
    }

    @Test
    fun entityToDto_carriesOwnershipAndServerRev_andOmitsPendingSync() {
        val dto = budgetEntity(id = "b", userId = "user-1", serverRev = 42, pendingSync = true).toDto()

        assertThat(dto.userId).isEqualTo("user-1")
        assertThat(dto.serverRev).isEqualTo(42)
        // BudgetDto has no pendingSync field at all — it can never go over the wire.
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = budgetDto(id = "b", serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
    }

    @Test
    fun entityToDto_roundTrips() {
        val original = budgetEntity(id = "b", serverRev = 9)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }
}
