package com.iponlove.app.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.accounts.FakeAccountDao
import com.iponlove.app.feature.accounts.accountEntity
import com.iponlove.app.feature.partnerdebt.FakePartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtSettlementDeletionEffects
import com.iponlove.app.feature.partnerdebt.debtPaymentEntity
import com.iponlove.app.feature.settings.data.ResetFinancesRepositoryImpl
import com.iponlove.app.feature.transactions.FakeTransactionDao
import com.iponlove.app.feature.transactions.transactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ResetFinancesRepositoryImplTest {

    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private var transactionRunCount = 0
    private val transactionRunner = LocalTransactionRunner { block ->
        transactionRunCount++
        block()
    }
    private val repository = ResetFinancesRepositoryImpl(
        transactionRunner = transactionRunner,
        transactionDao = transactionDao,
        accountDao = accountDao,
        clock = clock,
        currentUser = currentUser,
    )

    @Test
    fun previewCounts_countsOwnTransactionsAndAccounts_excludingSharedPartnerDeleted() = runTest {
        transactionDao.store["t-own"] = transactionEntity(id = "t-own", userId = "user-1")
        transactionDao.store["t-deleted"] = transactionEntity(id = "t-deleted", userId = "user-1", isDeleted = true)
        transactionDao.store["t-partner"] = transactionEntity(id = "t-partner", userId = "partner")

        accountDao.store["a-own"] = accountEntity(id = "a-own", userId = "user-1")
        accountDao.store["a-archived"] = accountEntity(id = "a-archived", userId = "user-1", isArchived = true)
        accountDao.store["a-shared"] = accountEntity(id = "a-shared", userId = null, coupleId = "couple-1")
        accountDao.store["a-partner"] = accountEntity(id = "a-partner", userId = "partner")

        val counts = repository.previewCounts()

        assertThat(counts.transactions).isEqualTo(1)
        // Own accounts include the archived one; shared + partner excluded.
        assertThat(counts.accounts).isEqualTo(2)
    }

    @Test
    fun reset_softDeletesOwnTransactions_andZeroesOwnAccountOpeningBalances() = runTest {
        transactionDao.store["t"] =
            transactionEntity(id = "t", userId = "user-1", updatedAt = Instant.ofEpochMilli(10_000))
        accountDao.store["a"] =
            accountEntity(id = "a", userId = "user-1", openingBalance = BigDecimal("500.00"), updatedAt = Instant.ofEpochMilli(10_000))
        accountDao.store["a-archived"] =
            accountEntity(id = "a-archived", userId = "user-1", isArchived = true, openingBalance = BigDecimal("250.00"), updatedAt = Instant.ofEpochMilli(10_000))
        now = Instant.ofEpochMilli(10_000) // same instant as each row's own updatedAt

        repository.reset()

        val expectedStamp = Instant.ofEpochMilli(10_001) // monotonic floor: prev + 1ms

        val t = transactionDao.store.getValue("t")
        assertThat(t.isDeleted).isTrue()
        assertThat(t.pendingSync).isTrue()
        assertThat(t.updatedAt).isEqualTo(expectedStamp)

        val a = accountDao.store.getValue("a")
        assertThat(a.openingBalance).isEqualTo(BigDecimal.ZERO)
        assertThat(a.isDeleted).isFalse() // account survives — only zeroed
        assertThat(a.pendingSync).isTrue()
        assertThat(a.updatedAt).isEqualTo(expectedStamp)

        // Archived owned accounts are zeroed too (still yours).
        assertThat(accountDao.store.getValue("a-archived").openingBalance).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun reset_leavesPartnerSharedAndAlreadyDeletedRowsUntouched() = runTest {
        transactionDao.store["t-partner"] = transactionEntity(id = "t-partner", userId = "partner")
        transactionDao.store["t-gone"] =
            transactionEntity(id = "t-gone", userId = "user-1", isDeleted = true, pendingSync = false)
        accountDao.store["a-shared"] =
            accountEntity(id = "a-shared", userId = null, coupleId = "couple-1", openingBalance = BigDecimal("999.00"))
        accountDao.store["a-partner"] =
            accountEntity(id = "a-partner", userId = "partner", openingBalance = BigDecimal("888.00"))

        repository.reset()

        assertThat(transactionDao.store.getValue("t-partner").pendingSync).isFalse()
        assertThat(transactionDao.store.getValue("t-gone").pendingSync).isFalse()
        // Shared couple account: not owned → opening balance untouched, not dirtied.
        val shared = accountDao.store.getValue("a-shared")
        assertThat(shared.openingBalance).isEqualTo(BigDecimal("999.00"))
        assertThat(shared.pendingSync).isFalse()
        // Partner's personal account: untouched.
        val partner = accountDao.store.getValue("a-partner")
        assertThat(partner.openingBalance).isEqualTo(BigDecimal("888.00"))
        assertThat(partner.pendingSync).isFalse()
    }

    @Test
    fun reset_runsBothMutationsInsideOneTransaction() = runTest {
        transactionDao.store["t"] = transactionEntity(id = "t", userId = "user-1")
        accountDao.store["a"] = accountEntity(id = "a", userId = "user-1")

        repository.reset()

        assertThat(transactionRunCount).isEqualTo(1)
    }

    @Test
    fun reset_retiresSettlementPayments_forWipedTransactions_inTheSameAtomicPass() = runTest {
        val debtDao = FakePartnerDebtDao()
        val debtRepository = PartnerDebtRepositoryImpl(debtDao, clock)
        val repositoryWithDebts = ResetFinancesRepositoryImpl(
            transactionRunner = transactionRunner,
            transactionDao = transactionDao,
            accountDao = accountDao,
            clock = clock,
            currentUser = currentUser,
            settlementEffects = PartnerDebtSettlementDeletionEffects(debtRepository),
        )
        // A lump settlement split across two debts (ADR-0055) — the whole group must retire.
        transactionDao.store["txn-pay"] = transactionEntity(id = "txn-pay", userId = "user-1", isSettlement = true)
        debtDao.payments["p-1"] = debtPaymentEntity(id = "p-1", debtId = "d-1", payorTxnId = "txn-pay")
        debtDao.payments["p-2"] = debtPaymentEntity(id = "p-2", debtId = "d-2", payorTxnId = "txn-pay")
        // An ordinary transaction — proves non-settlement wipes never touch the debt tables.
        transactionDao.store["t-plain"] = transactionEntity(id = "t-plain", userId = "user-1")

        repositoryWithDebts.reset()

        assertThat(debtDao.payments.getValue("p-1").isDeleted).isTrue()
        assertThat(debtDao.payments.getValue("p-1").pendingSync).isTrue()
        assertThat(debtDao.payments.getValue("p-2").isDeleted).isTrue()
        // Both the transaction wipe and the debt-payment retirement ran under one runner call.
        assertThat(transactionRunCount).isEqualTo(1)
    }

    @Test
    fun reset_ignoresPartnerOwnedSettlementTransactions() = runTest {
        val debtDao = FakePartnerDebtDao()
        val debtRepository = PartnerDebtRepositoryImpl(debtDao, clock)
        val repositoryWithDebts = ResetFinancesRepositoryImpl(
            transactionRunner = transactionRunner,
            transactionDao = transactionDao,
            accountDao = accountDao,
            clock = clock,
            currentUser = currentUser,
            settlementEffects = PartnerDebtSettlementDeletionEffects(debtRepository),
        )
        // Not owned by user-1 — activeOwnedBy(userId) excludes it, so it's never wiped here.
        transactionDao.store["t-partner-settle"] =
            transactionEntity(id = "t-partner-settle", userId = "partner", isSettlement = true)
        debtDao.payments["p-partner"] = debtPaymentEntity(id = "p-partner", debtId = "d", payorTxnId = "t-partner-settle")

        repositoryWithDebts.reset()

        assertThat(debtDao.payments.getValue("p-partner").isDeleted).isFalse()
    }
}
