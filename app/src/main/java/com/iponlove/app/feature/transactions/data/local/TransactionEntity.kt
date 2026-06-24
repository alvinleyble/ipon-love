package com.iponlove.app.feature.transactions.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `transactions` row. Implements [SyncMeta] for the generic sync engine.
 *
 * No Room foreign keys to accounts/categories on purpose: pulled rows can arrive before
 * their parents (the engine handles FK order, ADR-0009), so a hard constraint would
 * reject otherwise-valid inserts. [recurringRuleId] is carried for sync fidelity even
 * though the recurring-rules feature isn't built yet.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val note: String?,
    val date: Instant,
    val isPrivate: Boolean,
    val recurringRuleId: String?,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
