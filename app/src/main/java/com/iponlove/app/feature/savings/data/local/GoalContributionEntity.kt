package com.iponlove.app.feature.savings.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `goal_contributions` row — one entry in a goal's append-only ledger.
 * Owned by its [userId] (the CONTRIBUTOR), so on a shared goal both partners write their own
 * independent rows; distinct ids never conflict under LWW (ADR-0025). Carries no `couple_id` /
 * `is_shared`: a contribution inherits its shared-ness from its parent goal.
 */
@Entity(tableName = "goal_contributions")
data class GoalContributionEntity(
    @PrimaryKey override val id: String,
    val goalId: String,
    val userId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
