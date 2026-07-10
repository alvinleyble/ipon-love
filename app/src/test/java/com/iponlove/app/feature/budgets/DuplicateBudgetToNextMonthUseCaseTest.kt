package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.DuplicateBudgetToNextMonthUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal

class DuplicateBudgetToNextMonthUseCaseTest {

    private val repository = FakeBudgetRepository()
    private val useCase = DuplicateBudgetToNextMonthUseCase(repository)

    @Test
    fun noExistingNextMonthBudget_createsOneWithSameAmountAndRollover() = runTest {
        val june = budget("june", categoryId = "cat-1", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)

        useCase(june, sameCategoryBudgets = listOf(june))

        val created = repository.upserted.single()
        assertThat(created.yearMonth).isEqualTo("2026-07")
        assertThat(created.categoryId).isEqualTo("cat-1")
        assertThat(created.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(created.rolloverEnabled).isTrue()
    }

    @Test
    fun existingNextMonthBudget_isUpdatedInPlace_toMatchThisMonth() = runTest {
        val june = budget("june", categoryId = "cat-1", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)
        val july = budget("july", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-07", rolloverEnabled = false)

        useCase(june, sameCategoryBudgets = listOf(june, july))

        val updated = repository.upserted.single()
        assertThat(updated.id).isEqualTo("july")
        assertThat(updated.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(updated.rolloverEnabled).isTrue()
    }

    @Test
    fun overallBudget_nullCategory_duplicatesCorrectly() = runTest {
        val june = budget("june", categoryId = null, amount = "20000.00", yearMonth = "2026-06")

        useCase(june, sameCategoryBudgets = listOf(june))

        val created = repository.upserted.single()
        assertThat(created.categoryId).isNull()
        assertThat(created.yearMonth).isEqualTo("2026-07")
    }

    private class FakeBudgetRepository : BudgetRepository {
        val upserted = mutableListOf<Budget>()
        override fun observeBudgets(): Flow<List<Budget>> = emptyFlow()
        override fun observeSharedBudgets(coupleId: String): Flow<List<Budget>> = emptyFlow()
        override suspend fun getBudget(id: String): Budget? = null
        override suspend fun countPersonalBudgets(yearMonth: String): Int = 0
        override suspend fun upsertBudget(budget: Budget) {
            upserted += budget
        }
        override suspend fun upsertSharedBudget(budget: Budget, coupleId: String) = Unit
        override suspend fun deleteBudget(id: String) = Unit
        override suspend fun purgeSharedBudgets() = Unit
    }
}
