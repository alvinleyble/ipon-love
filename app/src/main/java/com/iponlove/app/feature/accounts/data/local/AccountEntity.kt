package com.iponlove.app.feature.accounts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of an `accounts` row. Implements [SyncMeta] so the generic sync engine
 * (BaseTableSyncer) can read its bookkeeping uniformly.
 *
 * Carries the columns the domain [com.iponlove.app.feature.accounts.domain.model.Account]
 * deliberately hides: `userId` (ownership) and the sync columns.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    val name: String,
    val type: AccountType,
    val openingBalance: BigDecimal,
    val icon: String?,
    val color: String?,
    val position: Int,
    val isArchived: Boolean,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
