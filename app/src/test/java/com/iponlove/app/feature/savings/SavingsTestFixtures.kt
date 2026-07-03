package com.iponlove.app.feature.savings

import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.data.remote.PartnerGoalContributionDto
import com.iponlove.app.feature.savings.data.remote.PartnerSavingsGoalDto
import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** In-memory [SavingsGoalDao] for fast JVM tests. */
class FakeSavingsGoalDao : SavingsGoalDao {
    val store = linkedMapOf<String, SavingsGoalEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeGoals(userId: String): Flow<List<SavingsGoalEntity>> =
        changes.map {
            store.values.filter { (it.userId == userId || it.isShared) && !it.isDeleted }
        }

    override suspend fun getById(id: String): SavingsGoalEntity? = store[id]
    override suspend fun upsert(goal: SavingsGoalEntity) { store[goal.id] = goal; changes.value++ }
    override suspend fun deleteById(id: String) { store.remove(id); changes.value++ }
    override suspend fun deleteNotOwnedBy(userId: String) {
        store.values.removeAll { it.userId != userId }; changes.value++
    }
    override suspend fun dirtyRows(): List<SavingsGoalEntity> = store.values.filter { it.pendingSync }
    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
    }
    override suspend fun applyPullBatch(goals: List<SavingsGoalEntity>) {
        goals.forEach { store[it.id] = it }; changes.value++
    }
}

/** In-memory [GoalContributionDao] for fast JVM tests. */
class FakeGoalContributionDao : GoalContributionDao {
    val store = linkedMapOf<String, GoalContributionEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeAllActive(): Flow<List<GoalContributionEntity>> =
        changes.map { store.values.filter { !it.isDeleted } }

    override fun observeByGoal(goalId: String): Flow<List<GoalContributionEntity>> =
        changes.map { store.values.filter { it.goalId == goalId && !it.isDeleted } }

    override suspend fun getById(id: String): GoalContributionEntity? = store[id]

    override suspend fun activeOwnedForGoal(goalId: String, userId: String): List<GoalContributionEntity> =
        store.values.filter { it.goalId == goalId && it.userId == userId && !it.isDeleted }

    override suspend fun upsert(contribution: GoalContributionEntity) {
        store[contribution.id] = contribution; changes.value++
    }
    override suspend fun deleteById(id: String) { store.remove(id); changes.value++ }
    override suspend fun deleteByGoalNotOwnedBy(goalId: String, userId: String) {
        store.values.removeAll { it.goalId == goalId && it.userId != userId }; changes.value++
    }
    override suspend fun deleteNotOwnedBy(userId: String) {
        store.values.removeAll { it.userId != userId }; changes.value++
    }
    override suspend fun dirtyRows(): List<GoalContributionEntity> = store.values.filter { it.pendingSync }
    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
    }
    override suspend fun applyPullBatch(contributions: List<GoalContributionEntity>) {
        contributions.forEach { store[it.id] = it }; changes.value++
    }
}

fun savingsGoalEntity(
    id: String,
    userId: String = "user-1",
    coupleId: String? = null,
    isShared: Boolean = false,
    name: String = "New phone",
    targetAmount: BigDecimal = BigDecimal("10000.00"),
    targetDate: LocalDate? = null,
    icon: String? = "savings",
    color: String? = "#43A047",
    isArchived: Boolean = false,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = SavingsGoalEntity(
    id, userId, coupleId, isShared, name, targetAmount, targetDate, icon, color,
    isArchived, createdAt, updatedAt, isDeleted, serverRev, pendingSync,
)

fun goalContributionEntity(
    id: String,
    goalId: String = "g-1",
    userId: String = "user-1",
    amount: BigDecimal = BigDecimal("500.00"),
    note: String? = null,
    date: Instant = Instant.ofEpochMilli(2_000),
    createdAt: Instant = Instant.ofEpochMilli(2_000),
    updatedAt: Instant = Instant.ofEpochMilli(2_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = GoalContributionEntity(
    id, goalId, userId, amount, note, date, createdAt, updatedAt, isDeleted, serverRev, pendingSync,
)

fun savingsGoal(
    id: String,
    name: String = "New phone",
    targetAmount: BigDecimal = BigDecimal("10000.00"),
    isShared: Boolean = false,
    isArchived: Boolean = false,
    isPartnerGoal: Boolean = false,
) = SavingsGoal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    targetDate = null,
    icon = "savings",
    color = "#43A047",
    isShared = isShared,
    isArchived = isArchived,
    isPartnerGoal = isPartnerGoal,
)

fun goalContribution(
    id: String,
    goalId: String = "g-1",
    amount: BigDecimal = BigDecimal("500.00"),
    byUserId: String = "user-1",
    isMine: Boolean = true,
) = GoalContribution(
    id = id,
    goalId = goalId,
    amount = amount,
    note = null,
    date = Instant.ofEpochMilli(2_000),
    byUserId = byUserId,
    isMine = isMine,
)

fun partnerSavingsGoalDto(
    id: String,
    userId: String = "partner-1",
    name: String? = "Trip to Japan",
    targetAmount: BigDecimal? = BigDecimal("50000.00"),
    targetDate: LocalDate? = null,
    icon: String? = "travel",
    color: String? = "#1E88E5",
    isArchived: Boolean? = false,
    isShared: Boolean = true,
    isDeleted: Boolean = false,
    coupleId: String? = "c-1",
    updatedAt: Instant = Instant.ofEpochMilli(3_000),
    serverRev: Long? = 10,
) = PartnerSavingsGoalDto(
    id, userId, name, targetAmount, targetDate, icon, color, isArchived,
    isShared, isDeleted, coupleId, updatedAt, serverRev,
)

fun partnerGoalContributionDto(
    id: String,
    goalId: String = "g-1",
    userId: String = "partner-1",
    amount: BigDecimal? = BigDecimal("750.00"),
    note: String? = null,
    date: Instant? = Instant.ofEpochMilli(4_000),
    isDeleted: Boolean = false,
    updatedAt: Instant = Instant.ofEpochMilli(4_000),
    serverRev: Long? = 11,
) = PartnerGoalContributionDto(
    id, goalId, userId, amount, note, date, isDeleted, updatedAt, serverRev,
)
