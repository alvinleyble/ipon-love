package com.iponlove.app.feature.partnerdebt.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `partner_debt_payments` row. Implements [SyncMeta] for the generic sync
 * engine. No Room FK to [PartnerDebtEntity] — the debt is synced as a separate table and
 * may not be present yet at pull time (ADR-0009); the calculator pairs by [debtId].
 */
@Entity(tableName = "partner_debt_payments", indices = [Index("debtId")])
data class DebtPaymentEntity(
    @PrimaryKey override val id: String,
    val debtId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
