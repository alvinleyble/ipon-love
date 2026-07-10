package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.ResetBudgetRolloverUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal

class ResetBudgetRolloverUseCaseTest {

    private val repository = FakeBudgetRepository()
    private val useCase = ResetBudgetRolloverUseCase(repository)

    @Test
    fun clearsRolloverOnTheTargetMonthItself_preservingItsOwnFields() = runTest {
        val june = budget("june", categoryId = "cat-1", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)

        useCase(june)

        val updated = repository.upserted.single()
        assertThat(updated.id).isEqualTo("june")
        assertThat(updated.yearMonth).isEqualTo("2026-06")
        assertThat(updated.categoryId).isEqualTo("cat-1")
        assertThat(updated.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(updated.rolloverEnabled).isFalse()
    }

    @Test
    fun doesNotTouchAnyOtherMonth() = runTest {
        val june = budget("june", categoryId = "cat-1", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)

        useCase(june)

        // Only M itself is written; no next-month row is created or modified.
        assertThat(repository.upserted).hasSize(1)
        assertThat(repository.upserted.single().yearMonth).isEqualTo("2026-06")
    }

    @Test
    fun rejectsWhenRolloverNotEnabled() = runTest {
        val june = budget("june", rolloverEnabled = false)

        val error = runCatching { useCase(june) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.upserted).isEmpty()
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
