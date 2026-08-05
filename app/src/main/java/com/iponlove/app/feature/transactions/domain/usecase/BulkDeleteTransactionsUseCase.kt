package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Bulk soft-delete behind Records' multi-select (v1.7.3 Item 7 / ADR-0064).
 *
 * It adds no delete semantics of its own — it funnels every row through
 * [DeleteTransactionUseCase], the single choke point that already cascades a transfer's linked
 * fee (ADR-0031) and retires a settlement leg's `DebtPayment` group (ADR-0065). What it does add
 * is atomicity across the whole selection ([LocalTransactionRunner.run], so a mid-batch failure
 * can't leave half the ticked rows gone with nothing saying which) and the dedupe below.
 */
class BulkDeleteTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val transactionRunner: LocalTransactionRunner = LocalTransactionRunner { block -> block() },
) {

    /**
     * What deleting [selectedIds] would actually do, without writing anything — the numbers the
     * confirmation dialog shows, computed by the same planner the write uses so the two can't drift.
     */
    suspend fun plan(selectedIds: Set<String>): BulkDeletePlan {
        if (selectedIds.isEmpty()) return BulkDeletePlan.EMPTY
        return planBulkDelete(selectedIds, repository.getActiveTransactions(selectedIds))
    }

    suspend operator fun invoke(selectedIds: Set<String>) {
        if (selectedIds.isEmpty()) return
        transactionRunner.run {
            // Re-planned inside the transaction rather than taking a caller-supplied plan: the
            // rows are read and written in the same atomic pass, so a sync landing between the
            // confirmation and the tap can't make the write act on a stale view of the ledger.
            val plan = planBulkDelete(selectedIds, repository.getActiveTransactions(selectedIds))
            plan.deletionRoots.forEach { id -> deleteTransaction(id) }
        }
    }
}

/**
 * The outcome of a bulk delete, decided before anything is written.
 *
 * The split between [deletionRoots] and [effectiveIds] is what keeps a transfer ticked *together
 * with its own fee row* from writing the fee twice (Item 7 Q3): the cascade in
 * [DeleteTransactionUseCase] already retires the fee when its transfer is deleted, so the fee is
 * dropped from the roots — while still counting toward what the user is told they're deleting.
 */
data class BulkDeletePlan(
    /** Ids [DeleteTransactionUseCase] is actually invoked on — the ticked rows, minus any fee row
     *  whose own transfer is ticked too, so each row is written exactly once. */
    val deletionRoots: List<String> = emptyList(),
    /** Every row that ends up tombstoned: the ticked rows ∪ the fee rows their transfers drag in.
     *  This is the count the confirmation names. */
    val effectiveIds: Set<String> = emptySet(),
    /** Fee rows pulled in that the user never ticked — named separately in the confirmation so the
     *  count isn't a surprise. */
    val untickedFeeCount: Int = 0,
    /** Ticked rows that are debt settlements (ADR-0065): deleting one puts its debt back to
     *  outstanding on both partners' boards, which the confirmation must say out loud. */
    val settlementCount: Int = 0,
) {
    val rowCount: Int get() = effectiveIds.size

    val isEmpty: Boolean get() = deletionRoots.isEmpty()

    companion object {
        val EMPTY = BulkDeletePlan()
    }
}

/**
 * Pure planner over [selectedIds] and the still-active [rows] behind them. Ids with no live row
 * (already deleted, or gone since the selection was made) simply drop out, so the count the user
 * sees never overstates what exists.
 */
internal fun planBulkDelete(selectedIds: Set<String>, rows: List<Transaction>): BulkDeletePlan {
    val selected = rows.filter { it.id in selectedIds }
    if (selected.isEmpty()) return BulkDeletePlan.EMPTY
    val cascadedFeeIds = selected.mapNotNull { it.transferFeeTransactionId }.toSet()
    return BulkDeletePlan(
        deletionRoots = selected.map { it.id }.filterNot { it in cascadedFeeIds },
        effectiveIds = LinkedHashSet<String>().apply {
            selected.forEach { add(it.id) }
            addAll(cascadedFeeIds)
        },
        untickedFeeCount = (cascadedFeeIds - selectedIds).size,
        settlementCount = selected.count { it.isSettlement },
    )
}
