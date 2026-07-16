package com.iponlove.app.feature.recurring.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room mirror of a `recurring_rules` row. Implements [SyncMeta] for the generic sync
 * engine. The schema's `template` jsonb is flattened into the `template*` columns; the
 * mapper re-nests it for the wire DTO. No Room FK to accounts/categories, for the same
 * sync-ordering reason as transactions (ADR-0009).
 */
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    val frequency: RecurringFrequency,
    val interval: Int,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val templateAmount: BigDecimal,
    val templateAccountId: String,
    val templateCategoryId: String,
    val templateNote: String?,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
    @ColumnInfo(defaultValue = "0") val isPaused: Boolean = false,
    @ColumnInfo(defaultValue = "0") val autoPost: Boolean = false,
) : SyncMeta
