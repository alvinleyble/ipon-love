package com.iponlove.app.feature.budgets

import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.budgets.data.remote.BudgetDto
import com.iponlove.app.feature.budgets.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory [BudgetDao] for fast JVM tests. */
class FakeBudgetDao : BudgetDao {
    val store = linkedMapOf<String, BudgetEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeBudgets(): Flow<List<BudgetEntity>> =
        changes.map { store.values.filter { !it.isDeleted } }

    override suspend fun getById(id: String): BudgetEntity? = store[id]

    override suspend fun upsert(budget: BudgetEntity) {
        store[budget.id] = budget
        changes.value++
    }

    override suspend fun dirtyRows(): List<BudgetEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(budgets: List<BudgetEntity>) {
        budgets.forEach { store[it.id] = it }
        changes.value++
    }
}

fun budget(
    id: String,
    categoryId: String? = "cat-1",
    amount: String = "5000.00",
    yearMonth: String = "2026-06",
) = Budget(id = id, categoryId = categoryId, amount = BigDecimal(amount), yearMonth = yearMonth)

fun budgetEntity(
    id: String,
    userId: String? = "user-1",
    coupleId: String? = null,
    categoryId: String? = "cat-1",
    amount: String = "5000.00",
    yearMonth: String = "2026-06",
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = BudgetEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    categoryId = categoryId,
    amount = BigDecimal(amount),
    yearMonth = yearMonth,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun budgetDto(
    id: String,
    categoryId: String? = "cat-1",
    amount: String = "5000.00",
    yearMonth: String = "2026-06",
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
) = BudgetDto(
    id = id,
    userId = "user-1",
    coupleId = null,
    categoryId = categoryId,
    amount = BigDecimal(amount),
    yearMonth = yearMonth,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
