package com.iponlove.app.feature.partnerdebt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One DAO for both partner-debt tables — they form a single feature and the sync engine
 * drives each table through its own [com.iponlove.app.core.sync.TableSyncer] via the
 * table-specific helpers below.
 */
@Dao
interface PartnerDebtDao {

    // ---- debts ----

    @Query("SELECT * FROM partner_debts WHERE isDeleted = 0 AND coupleId = :coupleId ORDER BY createdAt DESC")
    fun observeDebts(coupleId: String): Flow<List<PartnerDebtEntity>>

    @Query("SELECT * FROM partner_debts WHERE id = :id")
    suspend fun getDebt(id: String): PartnerDebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(debt: PartnerDebtEntity)

    // ---- payments ----

    /** All active payments — the local table only ever holds the current couple's rows. */
    @Query("SELECT * FROM partner_debt_payments WHERE isDeleted = 0 ORDER BY date DESC")
    fun observePayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM partner_debt_payments WHERE id = :id")
    suspend fun getPayment(id: String): DebtPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: DebtPaymentEntity)

    // ---- unpair purge (ADR-0008): RLS hides these the instant the couple dissolves, so the
    // server tombstone can never reach the device — purge locally. Both tables only ever hold
    // the current couple's rows, so a bare delete is the full purge.

    @Query("DELETE FROM partner_debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM partner_debt_payments")
    suspend fun deleteAllPayments()

    // ---- sync engine plumbing: debts ----

    @Query("SELECT * FROM partner_debts WHERE pendingSync = 1")
    suspend fun dirtyDebts(): List<PartnerDebtEntity>

    @Query("UPDATE partner_debts SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearDebtPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyDebtBatch(debts: List<PartnerDebtEntity>)

    // ---- sync engine plumbing: payments ----

    @Query("SELECT * FROM partner_debt_payments WHERE pendingSync = 1")
    suspend fun dirtyPayments(): List<DebtPaymentEntity>

    @Query("UPDATE partner_debt_payments SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPaymentPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPaymentBatch(payments: List<DebtPaymentEntity>)
}
