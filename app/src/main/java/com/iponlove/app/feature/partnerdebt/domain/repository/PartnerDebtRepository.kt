package com.iponlove.app.feature.partnerdebt.domain.repository

import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import kotlinx.coroutines.flow.Flow

/**
 * Partner-debt source of truth (Room-backed). Debts and payments are couple-shared,
 * read/write by both partners (no redaction — debts are inherently joint data, ADR-0011);
 * they sync bidirectionally like shared budgets. All writes funnel through here so the
 * sync bookkeeping — `updated_at` (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place.
 */
interface PartnerDebtRepository {

    /** Active debts for [coupleId], newest first. */
    fun observeDebts(coupleId: String): Flow<List<PartnerDebt>>

    /**
     * All active payments. The local table only ever holds the current couple's payments
     * (RLS-scoped, purged on unpair), so no couple filter is needed; the calculator pairs
     * each payment to its debt by [DebtPayment.debtId].
     */
    fun observePayments(): Flow<List<DebtPayment>>

    suspend fun getDebt(id: String): PartnerDebt?

    /** One-shot snapshot of active debts for the netting reconcile. */
    suspend fun getActiveDebts(coupleId: String): List<PartnerDebt>

    /** One-shot snapshot of active payments for the netting reconcile. */
    suspend fun getActivePayments(): List<DebtPayment>

    /** Create or edit a couple-owned debt under [coupleId]; ownership survives edits. */
    suspend fun upsertDebt(debt: PartnerDebt, coupleId: String)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteDebt(id: String)

    /** Record a (partial or full) repayment against an existing debt. */
    suspend fun upsertPayment(payment: DebtPayment)

    /**
     * Hard-delete every debt and payment on unpair (ADR-0008). RLS hides them the instant
     * the couple dissolves, so the device can never receive their tombstones — it purges
     * locally off the same signal shared budgets use. No cursor reset: the global
     * `server_rev` sequence means a re-pairing's fresh debts always land above the cursor.
     */
    suspend fun purgeCoupleDebts()
}
