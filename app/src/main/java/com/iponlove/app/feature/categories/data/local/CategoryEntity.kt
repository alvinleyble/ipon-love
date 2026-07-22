package com.iponlove.app.feature.categories.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import com.iponlove.app.feature.categories.domain.model.CategoryType
import java.time.Instant

/**
 * Room mirror of a `categories` row. Implements [SyncMeta] so the generic sync engine
 * can read its bookkeeping uniformly. Carries the columns the domain
 * [com.iponlove.app.feature.categories.domain.model.Category] hides: `userId` and the
 * sync columns.
 *
 * Personal category: [userId] set, [coupleId] null. Shared (couple-owned, ADR-0018): the
 * reverse. [createdBy] records the creator for revert-to-creator on un-share/unpair.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey override val id: String,
    val userId: String?,
    val coupleId: String?,
    val createdBy: String?,
    val name: String,
    val type: CategoryType,
    val icon: String?,
    val color: String?,
    val position: Int,
    val isArchived: Boolean,
    // Pass-through categories (e.g. reimbursable work expenses): excluded from Analysis/Budgets/
    // Combined but still in Records + balance (ADR-0049). NOT NULL default false — AutoMigration 27→28.
    @ColumnInfo(defaultValue = "0") val excludeFromAnalysis: Boolean = false,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
