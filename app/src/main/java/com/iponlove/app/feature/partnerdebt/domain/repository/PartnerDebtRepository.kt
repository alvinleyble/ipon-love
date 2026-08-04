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
     * Stamp [receiverTxnId] onto every settlement payment the payor's [payorTxnId] backs, when
     * the receiver (lender) adds the matching income to their own account (ADR-0019 #14). One
     * lump may have been split across several debts (ADR-0055), so the group — not a single
     * payment — is the unit. First writer wins per row: a payment that already has a receiver
     * leg is left untouched.
     */
    suspend fun stampReceiverTxn(payorTxnId: String, receiverTxnId: String)

    /**
     * Soft delete (ADR-0010) every active payment the payor's settlement expense [payorTxnId]
     * backs — the inverse of the write
     * [com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtsUseCase] performs
     * (ADR-0065). Deleting that expense means the money it recorded is gone from the ledger,
     * so the debts it paid down
     * must read as outstanding again. A lump split across several debts (ADR-0055) retires the
     * whole group, not just one payment. No-op (idempotent) once the group is already retired.
     */
    suspend fun retirePaymentsForPayorTxn(payorTxnId: String)

    /**
     * Clear [DebtPayment.receiverTxnId] on every active payment stamped with the lender's
     * settlement income [receiverTxnId] — the inverse of [stampReceiverTxn] (ADR-0065). Deleting
     * that income means the receiver leg never happened, so the board must re-offer "add to my
     * account" instead of believing it's done. Only the stamp is cleared — [DebtPayment.amount]
     * and [DebtPayment.debtId] are untouched. No-op (idempotent) once already cleared.
     */
    suspend fun clearReceiverStamp(receiverTxnId: String)

    /**
     * Hard-delete every debt and payment on unpair (ADR-0008). RLS hides them the instant
     * the couple dissolves, so the device can never receive their tombstones — it purges
     * locally off the same signal shared budgets use. No cursor reset: the global
     * `server_rev` sequence means a re-pairing's fresh debts always land above the cursor.
     */
    suspend fun purgeCoupleDebts()
}
