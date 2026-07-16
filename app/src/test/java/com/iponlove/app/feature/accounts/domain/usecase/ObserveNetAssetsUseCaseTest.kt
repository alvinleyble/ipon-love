package com.iponlove.app.feature.accounts.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.transactions.domain.usecase.ObserveBalanceLedgerUseCase
import com.iponlove.app.feature.transactions.txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * "Net assets" (Item 14) — the total balance across active accounts that Accounts and Analysis
 * both display. The derivation math itself is proven in `AccountBalanceCalculatorTest`; these
 * cases lock the one piece of new logic — summing derived balances over non-archived accounts.
 */
class ObserveNetAssetsUseCaseTest {

    private class FakeAccountRepository(private val accounts: List<Account>) : AccountRepository {
        override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
            flowOf(if (includeArchived) accounts else accounts.filterNot { it.isArchived })
        override suspend fun getAccount(id: String): Account? = accounts.find { it.id == id }
        override suspend fun countOwnedAccounts(): Int = TODO("not needed")
        override suspend fun countSharedAccounts(): Int = TODO("not needed")
        override suspend fun upsertAccount(account: Account): Unit = TODO("not needed")
        override suspend fun reorderAccounts(orderedIds: List<String>): Unit = TODO("not needed")
        override suspend fun setArchived(id: String, archived: Boolean): Unit = TODO("not needed")
        override suspend fun deleteAccount(id: String): Unit = TODO("not needed")
        override suspend fun shareAccount(id: String, coupleId: String): Unit = TODO("not needed")
        override suspend fun unshareAccount(id: String): Unit = TODO("not needed")
        override suspend fun purgePartnerData(): Unit = TODO("not needed")
    }

    private class FakeTransactionRepository(private val ledger: List<Transaction>) : TransactionRepository {
        override fun observeBalanceLedger(): Flow<List<Transaction>> = flowOf(ledger)
        override fun observeTransactions(): Flow<List<Transaction>> = TODO("not needed")
        override fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> =
            TODO("not needed")
        override fun observeHasAnyTransaction(): Flow<Boolean> = TODO("not needed")
        override fun observeMaterializedRecurringIds(): Flow<Set<String>> = TODO("not needed")
        override fun observeCombinedTransactions(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<OwnedTransaction>> = TODO("not needed")
        override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = TODO("not needed")
        override suspend fun getTransaction(id: String): Transaction? = TODO("not needed")
        override suspend fun upsertTransaction(transaction: Transaction): Unit = TODO("not needed")
        override suspend fun deleteTransaction(id: String): Unit = TODO("not needed")
        override suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String): Boolean =
            TODO("not needed")
        override suspend fun purgePartnerData(): Unit = TODO("not needed")
    }

    private fun account(id: String, opening: String, archived: Boolean = false) = Account(
        id = id,
        name = id,
        type = AccountType.EWALLET,
        openingBalance = BigDecimal(opening),
        isArchived = archived,
    )

    private fun useCase(accounts: List<Account>, ledger: List<Transaction> = emptyList()) =
        ObserveNetAssetsUseCase(
            ObserveAccountsUseCase(FakeAccountRepository(accounts)),
            ObserveBalanceLedgerUseCase(FakeTransactionRepository(ledger)),
        )

    @Test
    fun `no accounts is zero`() = runTest {
        assertThat(useCase(emptyList())().first()).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `sums opening balances with no transactions`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "100.00"))
        assertThat(useCase(accounts)().first()).isEqualTo(BigDecimal("600.00"))
    }

    @Test
    fun `sums derived balances across accounts`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "100.00"))
        val ledger = listOf(
            txn("t1", TransactionType.EXPENSE, "50.00", accountId = "acc-1"),
            txn("t2", TransactionType.INCOME, "20.00", accountId = "acc-2"),
        )
        // (500 - 50) + (100 + 20) = 570.00
        assertThat(useCase(accounts, ledger)().first()).isEqualTo(BigDecimal("570.00"))
    }

    @Test
    fun `archived accounts are excluded from the sum`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "1000.00", archived = true))
        assertThat(useCase(accounts)().first()).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun `a transfer between an active and an archived account only counts the active side`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "0.00", archived = true))
        val ledger = listOf(txn("t1", TransactionType.TRANSFER, "100.00", accountId = "acc-1", toAccountId = "acc-2"))
        // acc-1 (active) drops to 400; acc-2 (archived) is excluded regardless of its balance.
        assertThat(useCase(accounts, ledger)().first()).isEqualTo(BigDecimal("400.00"))
    }
}
