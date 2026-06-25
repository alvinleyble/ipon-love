package com.iponlove.app.feature.partnerdebt.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `partner_debts` row. Implements [SyncMeta] for the generic sync engine.
 * Always couple-owned ([coupleId] non-null) — debts only exist within a couple. No Room FK
 * to users/couples, for the same sync-ordering reason as transactions (ADR-0009).
 */
@Entity(tableName = "partner_debts", indices = [Index("coupleId")])
data class PartnerDebtEntity(
    @PrimaryKey override val id: String,
    val coupleId: String,
    val borrowerId: String,
    val lenderId: String,
    val amount: BigDecimal,
    val description: String?,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
