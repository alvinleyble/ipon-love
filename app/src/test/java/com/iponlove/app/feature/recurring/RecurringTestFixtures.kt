package com.iponlove.app.feature.recurring

import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleDto
import com.iponlove.app.feature.recurring.data.remote.RecurringTemplateDto
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.model.RecurringTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** In-memory [RecurringRuleDao] for fast JVM tests. */
class FakeRecurringRuleDao : RecurringRuleDao {
    val store = linkedMapOf<String, RecurringRuleEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeRules(): Flow<List<RecurringRuleEntity>> =
        changes.map { store.values.filter { !it.isDeleted } }

    override suspend fun activeRules(): List<RecurringRuleEntity> =
        store.values.filter { !it.isDeleted }

    override suspend fun getById(id: String): RecurringRuleEntity? = store[id]

    override suspend fun upsert(rule: RecurringRuleEntity) {
        store[rule.id] = rule
        changes.value++
    }

    override suspend fun dirtyRows(): List<RecurringRuleEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(rules: List<RecurringRuleEntity>) {
        rules.forEach { store[it.id] = it }
        changes.value++
    }
}

fun rule(
    id: String,
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    interval: Int = 1,
    nextDate: LocalDate = LocalDate.of(2026, 6, 1),
    endDate: LocalDate? = null,
    amount: String = "1000.00",
    accountId: String = "acc-1",
    categoryId: String = "cat-1",
    note: String? = null,
    isPaused: Boolean = false,
    autoPost: Boolean = false,
) = RecurringRule(
    id = id,
    frequency = frequency,
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    template = RecurringTemplate(
        amount = BigDecimal(amount),
        accountId = accountId,
        categoryId = categoryId,
        note = note,
    ),
    isPaused = isPaused,
    autoPost = autoPost,
)

fun ruleEntity(
    id: String,
    userId: String = "user-1",
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    interval: Int = 1,
    nextDate: LocalDate = LocalDate.of(2026, 6, 1),
    endDate: LocalDate? = null,
    amount: String = "1000.00",
    accountId: String = "acc-1",
    categoryId: String = "cat-1",
    note: String? = null,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
    isPaused: Boolean = false,
    autoPost: Boolean = false,
) = RecurringRuleEntity(
    id = id,
    userId = userId,
    frequency = frequency,
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    templateAmount = BigDecimal(amount),
    templateAccountId = accountId,
    templateCategoryId = categoryId,
    templateNote = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
    isPaused = isPaused,
    autoPost = autoPost,
)

fun ruleDto(
    id: String,
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    interval: Int = 1,
    nextDate: LocalDate = LocalDate.of(2026, 6, 1),
    endDate: LocalDate? = null,
    amount: String = "1000.00",
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    isPaused: Boolean = false,
    autoPost: Boolean = false,
) = RecurringRuleDto(
    id = id,
    userId = "user-1",
    frequency = frequency.name,
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    template = RecurringTemplateDto(
        amount = BigDecimal(amount),
        accountId = "acc-1",
        categoryId = "cat-1",
        note = null,
    ),
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    isPaused = isPaused,
    autoPost = autoPost,
)

fun category(id: String, type: CategoryType, name: String = id) =
    Category(id = id, name = name, type = type)

/** In-memory [CategoryRepository] supplying a fixed (or mutable via [supply]) category list. */
class FakeCategoryRepository(
    private val supply: () -> List<Category>,
) : CategoryRepository {
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> = flowOf(supply())
    override fun observeAllCategories(): Flow<List<Category>> = flowOf(supply())
    override suspend fun getCategory(id: String): Category? = supply().firstOrNull { it.id == id }
    override suspend fun countOwnedCategories(): Int = error("unused")
    override suspend fun countSharedCategories(): Int = error("unused")
    override suspend fun upsertCategory(category: Category) = error("unused")
    override suspend fun reorderCategories(orderedIds: List<String>) = error("unused")
    override suspend fun setArchived(id: String, archived: Boolean) = error("unused")
    override suspend fun deleteCategory(id: String) = error("unused")
    override suspend fun shareCategory(id: String, coupleId: String) = error("unused")
    override suspend fun unshareCategory(id: String) = error("unused")
    override suspend fun purgePartnerData() = error("unused")
}
