package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Bulk-purges every replicated partner row after an unpair (ADR-0008). RLS stops returning
 * partner rows the moment `couple_id` goes null, so the device can never receive per-row
 * removal markers — it must purge locally off the single signal that its own `couple_id`
 * cleared.
 *
 * Also resets the partner-view pull cursors to 0: a future pairing must re-pull the new
 * partner's full history, whose `server_rev` values sit *below* the cursor left behind by
 * the previous couple — without a reset those rows would be skipped.
 *
 * Shared budgets ride the same one-way removal: they live in the local `budgets` table and
 * RLS hides them the instant the couple dissolves, so they're purged here too. No cursor
 * reset for them — they share the `BUDGETS` cursor with personal budgets (which keeps
 * advancing), and a re-pairing's new shared budgets carry fresh, higher `server_rev`.
 */
class PurgePartnerReplicaUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val noteRepository: NoteRepository,
    private val budgetRepository: BudgetRepository,
    private val cursors: SyncCursorStore,
) {
    suspend operator fun invoke() {
        accountRepository.purgePartnerData()
        categoryRepository.purgePartnerData()
        transactionRepository.purgePartnerData()
        noteRepository.purgePartnerData()
        budgetRepository.purgeSharedBudgets()

        cursors.setCursor(SyncTable.PARTNER_ACCOUNTS, 0)
        cursors.setCursor(SyncTable.PARTNER_CATEGORIES, 0)
        cursors.setCursor(SyncTable.PARTNER_TRANSACTIONS, 0)
        cursors.setCursor(SyncTable.PARTNER_NOTES, 0)
    }
}
