package com.iponlove.app.feature.savings.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room mirror of a `savings_goals` row. Implements [SyncMeta] so the generic sync engine
 * reads its bookkeeping uniformly. Carries the columns the domain
 * [com.iponlove.app.feature.savings.domain.model.SavingsGoal] hides: `userId` (the creator/
 * owner) and the sync columns.
 *
 * Personal goal: [isShared] false, [coupleId] null. Shared (ADR-0025): the generic sharing
 * layer — [isShared] true + [coupleId] set, exactly like notes. `savedAmount` is NOT a column:
 * it is DERIVED from [GoalContributionEntity] rows so a shared counter can't clobber under LWW.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    val coupleId: String?,
    val isShared: Boolean,
    val name: String,
    val targetAmount: BigDecimal,
    val targetDate: LocalDate?,
    val icon: String?,
    val color: String?,
    val isArchived: Boolean,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
