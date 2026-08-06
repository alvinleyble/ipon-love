package com.iponlove.app.feature.drafts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.drafts.data.TransactionDraftRepositoryImpl
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.transactions.transactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * **The load-bearing test of ADR-0066 — and it passes trivially, which is the point.**
 *
 * A parked draft must be invisible to every money calculation, and under the locked schema it is
 * invisible *by construction*: a draft is not a row in the ledger table, so there is no
 * `WHERE is_draft = 0` predicate to forget. The rejected `is_draft`-flag design would have needed
 * that predicate in ~19 places with no compile-time membership check, and the failure mode of
 * missing one is a half-typed draft counting as money spent.
 *
 * This test exists so that guarantee is *asserted* rather than merely argued: a future refactor
 * that merges the two tables fails here, loudly, instead of shipping.
 */
class DraftsAreNotMoneyTest {

    private val transactionDao = FakeTransactionDao()
    private val draftDao = FakeTransactionDraftDao()
    private val clock = SyncClock(now = { Instant.parse("2026-08-06T10:00:00Z") })
    private val currentUser = CurrentUserProvider { "user-1" }

    private val transactions = TransactionRepositoryImpl(
        dao = transactionDao,
        clock = clock,
        currentUser = currentUser,
    )
    private val drafts = TransactionDraftRepositoryImpl(
        dao = draftDao,
        clock = clock,
        currentUser = currentUser,
    )

    /** One real ₱100 expense, one parked ₱5,000 draft. Every ledger read must see only the first. */
    @Test
    fun aParkedDraftIsInvisibleToTheLedgerAndToDerivedBalance() = runTest {
        transactionDao.store["t1"] = transactionEntity("t1", amount = "100.00")
        drafts.saveDraft(draft(id = "d1", amount = BigDecimal("5000.00")))

        val ledger = transactions.observeTransactions().first()
        assertThat(ledger.map { it.id }).containsExactly("t1")

        val balance = AccountBalanceCalculator.balanceOf(
            accountId = "acc-1",
            openingBalance = BigDecimal("1000.00"),
            transactions = transactions.observeBalanceLedger().first(),
        )
        assertThat(balance).isEqualTo(BigDecimal("900.00"))
    }

    /**
     * The subtlest thing the rejected schema would have broken (contract §6.2): first-run starter
     * seeding is gated on the ledger being empty, so a draft counting as a transaction would have
     * silently suppressed onboarding for a user who had recorded nothing at all.
     */
    @Test
    fun observeHasAnyTransaction_staysFalseWithOnlyDraftsPresent() = runTest {
        drafts.saveDraft(draft(id = "d1"))

        assertThat(transactions.observeHasAnyTransaction().first()).isFalse()
    }

    /**
     * `observeCombined` carries no owner filter by design (it is the couple feed), which is why a
     * draft landing in the transactions table would have gone straight to the partner. It cannot:
     * drafts are a different table, and there is no partner view over it at all.
     */
    @Test
    fun theCombinedCoupleFeedNeverSeesADraft() = runTest {
        transactionDao.store["t1"] = transactionEntity("t1", amount = "100.00")
        drafts.saveDraft(draft(id = "d1", amount = BigDecimal("5000.00")))

        val combined = transactions.observeCombinedTransactionsUnbounded().first()

        assertThat(combined.map { it.id }).containsExactly("t1")
    }
}
