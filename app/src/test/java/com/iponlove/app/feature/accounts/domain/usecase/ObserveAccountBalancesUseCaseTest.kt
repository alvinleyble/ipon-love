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
 * Per-account balances for the balance widget's tall form (Item 33). The derivation math is proven
 * in `AccountBalanceCalculatorTest`; these cases lock this use case's contract — active accounts
 * only, in the repository's (position) order, each paired with its derived balance so the rows sum
 * to `ObserveNetAssetsUseCase`.
 */
class ObserveAccountBalancesUseCaseTest {

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
        override fun observeCombinedTransactionsUnbounded(): Flow<List<Transaction>> = TODO("not needed")
        override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = TODO("not needed")
        override suspend fun getTransaction(id: String): Transaction? = TODO("not needed")
        override suspend fun countByCategory(categoryId: String): Int = TODO("not needed")
        override suspend fun countByAccount(accountId: String): Int = TODO("not needed")
        override suspend fun upsertTransaction(transaction: Transaction): Unit = TODO("not needed")
        override suspend fun deleteTransaction(id: String): Unit = TODO("not needed")
        override suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String): Boolean =
            TODO("not needed")
        override suspend fun purgePartnerData(): Unit = TODO("not needed")
    }

    private fun account(
        id: String,
        opening: String,
        archived: Boolean = false,
        shared: Boolean = false,
    ) = Account(
        id = id,
        name = id,
        type = AccountType.EWALLET,
        openingBalance = BigDecimal(opening),
        isArchived = archived,
        isShared = shared,
    )

    private fun useCase(accounts: List<Account>, ledger: List<Transaction> = emptyList()) =
        ObserveAccountBalancesUseCase(
            ObserveAccountsUseCase(FakeAccountRepository(accounts)),
            ObserveBalanceLedgerUseCase(FakeTransactionRepository(ledger)),
        )

    @Test
    fun `no accounts is an empty list`() = runTest {
        assertThat(useCase(emptyList())().first()).isEmpty()
    }

    @Test
    fun `pairs each active account with its opening balance in order`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "100.00"))
        val rows = useCase(accounts)().first()
        assertThat(rows.map { it.account.id }).containsExactly("acc-1", "acc-2").inOrder()
        assertThat(rows.map { it.balance })
            .containsExactly(BigDecimal("500.00"), BigDecimal("100.00")).inOrder()
    }

    @Test
    fun `derives each balance from the ledger`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "100.00"))
        val ledger = listOf(
            txn("t1", TransactionType.EXPENSE, "50.00", accountId = "acc-1"),
            txn("t2", TransactionType.INCOME, "20.00", accountId = "acc-2"),
        )
        val rows = useCase(accounts, ledger)().first()
        assertThat(rows.single { it.account.id == "acc-1" }.balance).isEqualTo(BigDecimal("450.00"))
        assertThat(rows.single { it.account.id == "acc-2" }.balance).isEqualTo(BigDecimal("120.00"))
    }

    @Test
    fun `archived accounts are excluded`() = runTest {
        val accounts = listOf(account("acc-1", "500.00"), account("acc-2", "999.00", archived = true))
        val rows = useCase(accounts)().first()
        assertThat(rows.map { it.account.id }).containsExactly("acc-1")
    }

    @Test
    fun `a shared account is included with its combined ledger balance`() = runTest {
        // The ledger already carries both partners' postings to a shared account (ADR-0018); the
        // use case just derives its balance like any other active account.
        val accounts = listOf(account("shared-1", "0.00", shared = true))
        val ledger = listOf(
            txn("mine", TransactionType.EXPENSE, "30.00", accountId = "shared-1"),
            txn("partner", TransactionType.INCOME, "80.00", accountId = "shared-1"),
        )
        val rows = useCase(accounts, ledger)().first()
        assertThat(rows.single().balance).isEqualTo(BigDecimal("50.00"))
    }
}
