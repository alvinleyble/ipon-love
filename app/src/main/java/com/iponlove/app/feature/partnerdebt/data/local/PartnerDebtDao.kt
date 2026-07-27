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

    /** One-shot snapshot of the couple's active debts — drives the netting reconcile. */
    @Query("SELECT * FROM partner_debts WHERE isDeleted = 0 AND coupleId = :coupleId")
    suspend fun activeDebts(coupleId: String): List<PartnerDebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(debt: PartnerDebtEntity)

    // ---- payments ----

    /** All active payments — the local table only ever holds the current couple's rows. */
    @Query("SELECT * FROM partner_debt_payments WHERE isDeleted = 0 ORDER BY date DESC")
    fun observePayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM partner_debt_payments WHERE id = :id")
    suspend fun getPayment(id: String): DebtPaymentEntity?

    /** One-shot snapshot of all active payments — drives the netting reconcile. */
    @Query("SELECT * FROM partner_debt_payments WHERE isDeleted = 0")
    suspend fun activePayments(): List<DebtPaymentEntity>

    /**
     * Every active payment backed by one payor expense. A lump settlement split across debts
     * (ADR-0055) writes several payments sharing one `payorTxnId`, and the receiver's income
     * leg stamps the whole group at once; a single-debt settlement returns a group of one.
     */
    @Query("SELECT * FROM partner_debt_payments WHERE payorTxnId = :payorTxnId AND isDeleted = 0")
    suspend fun paymentsForPayorTxn(payorTxnId: String): List<DebtPaymentEntity>

    /**
     * Active netting payments touching [debtId] — either attached to it or referencing it
     * as the counter-debt. Used to cascade soft-delete when the parent debt is deleted.
     */
    @Query(
        "SELECT * FROM partner_debt_payments WHERE (debtId = :debtId OR counterDebtId = :debtId) AND isNetting = 1 AND isDeleted = 0",
    )
    suspend fun nettingPaymentsForDebt(debtId: String): List<DebtPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: DebtPaymentEntity)

    // ---- unpair purge (ADR-0008): RLS hides these the instant the couple dissolves, so the
    // server tombstone can never reach the device — purge locally. Both tables only ever hold
    // the current couple's rows, so a bare delete is the full purge.

    @Query("DELETE FROM partner_debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM partner_debt_payments")
    suspend fun deleteAllPayments()

    // ---- push ownership resolution (v1.6.5 Item 20 follow-up) ----

    /** This session's current couple, from the local users row (same source as AccountDao). */
    @Query("SELECT coupleId FROM users WHERE id = :userId")
    suspend fun coupleIdOf(userId: String): String?

    /**
     * Ids of every local debt belonging to [coupleId] (deleted included — the payment RLS
     * subquery has no is_deleted filter, so a payment against a soft-deleted-but-same-couple
     * debt still pushes). Used to decide which dirty payments this session can own.
     */
    @Query("SELECT id FROM partner_debts WHERE coupleId = :coupleId")
    suspend fun debtIdsForCouple(coupleId: String): List<String>

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
