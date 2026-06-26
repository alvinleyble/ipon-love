package com.iponlove.app.feature.partnerdebt.data.local

import androidx.room.ColumnInfo
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
 *
 * [isNetting] / [counterDebtId] mark an auto-generated offset against an opposing debt
 * (ADR-0019 #9): the row's id is deterministic so concurrent offline netting converges.
 */
@Entity(tableName = "partner_debt_payments", indices = [Index("debtId")])
data class DebtPaymentEntity(
    @PrimaryKey override val id: String,
    val debtId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    @ColumnInfo(defaultValue = "0") val isNetting: Boolean = false,
    val counterDebtId: String? = null,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
