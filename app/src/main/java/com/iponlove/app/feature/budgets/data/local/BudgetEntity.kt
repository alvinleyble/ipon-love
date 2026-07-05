package com.iponlove.app.feature.budgets.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `budgets` row. Implements [SyncMeta] for the generic sync engine.
 *
 * Personal budget: [userId] set, [coupleId] null. Shared budget: the reverse (arrives
 * with the couple feature). [categoryId] null = overall monthly budget. No Room FK to
 * categories, for the same sync-ordering reason as transactions (ADR-0009).
 */
@Entity(tableName = "budgets", indices = [Index("categoryId"), Index("yearMonth")])
data class BudgetEntity(
    @PrimaryKey override val id: String,
    val userId: String?,
    val coupleId: String?,
    val categoryId: String?,
    val amount: BigDecimal,
    val yearMonth: String,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
    @ColumnInfo(defaultValue = "0") val rolloverEnabled: Boolean = false,
) : SyncMeta
