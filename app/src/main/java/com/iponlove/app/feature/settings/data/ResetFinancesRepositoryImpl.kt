package com.iponlove.app.feature.settings.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.core.sync.bulkRestamp
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts
import com.iponlove.app.feature.settings.domain.repository.ResetFinancesRepository
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Reset finances (ADR-0037): a "fresh start" that keeps every structure/definition but zeroes
 * the numbers. Two owned-row mutations, run atomically in one local transaction, then a single
 * interactive push:
 *
 *  - **Transactions** — soft-deleted (tombstone + push), clearing all history.
 *  - **Personal accounts** — `opening_balance` set to ₱0, so balances read ₱0 once the ledger
 *    is empty (shared couple accounts carry a null `userId` and are skipped by [AccountDao.activeOwnedBy]).
 *
 * Everything else is untouched: categories, budgets, recurring rules, savings goals + their
 * contributions, notes, and all couple/shared state. Budget "spent" and account balances are
 * both derived, so they fall to ₱0 on their own once transactions are gone.
 */
class ResetFinancesRepositoryImpl @Inject constructor(
    private val transactionRunner: LocalTransactionRunner,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : ResetFinancesRepository {

    override suspend fun previewCounts(): ResetFinancesCounts {
        val userId = currentUser.userId()
        return ResetFinancesCounts(
            transactions = transactionDao.activeOwnedBy(userId).size,
            accounts = accountDao.activeOwnedBy(userId).size,
        )
    }

    override suspend fun reset() {
        val userId = currentUser.userId()
        transactionRunner.run {
            bulkRestamp(
                clock = clock,
                fetch = { transactionDao.activeOwnedBy(userId) },
                transform = { row, ts -> row.copy(isDeleted = true, updatedAt = ts, pendingSync = true) },
                upsert = transactionDao::upsert,
            )
            bulkRestamp(
                clock = clock,
                fetch = { accountDao.activeOwnedBy(userId) },
                transform = { row, ts -> row.copy(openingBalance = BigDecimal.ZERO, updatedAt = ts, pendingSync = true) },
                upsert = accountDao::upsert,
            )
        }
        syncTrigger.requestPush()
    }
}
