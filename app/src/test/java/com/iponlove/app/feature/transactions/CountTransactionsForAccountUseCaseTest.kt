package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.transactions.domain.usecase.CountTransactionsForAccountUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The count that drives the account delete-confirm's "Used by N transactions (incl. transfers)"
 * copy + archive-steer (v1.6.7 Item 5). The correctness-critical bit — that a transfer whose
 * *destination* is this account counts (`accountId = :id OR toAccountId = :id`) — lives in the DAO
 * SQL and is verified on-device (no JVM Room harness); this locks the domain wiring: the use case
 * delegates to [TransactionRepository.countByAccount] and returns its result verbatim.
 */
class CountTransactionsForAccountUseCaseTest {

    private val repository = mockk<TransactionRepository>()
    private val useCase = CountTransactionsForAccountUseCase(repository)

    @Test
    fun invoke_returnsRepositoryCountForTheGivenAccount() = runTest {
        coEvery { repository.countByAccount("acct-1") } returns 4

        val count = useCase("acct-1")

        assertThat(count).isEqualTo(4)
        coVerify(exactly = 1) { repository.countByAccount("acct-1") }
    }

    @Test
    fun invoke_zeroWhenNoTransactionsReferenceTheAccount() = runTest {
        coEvery { repository.countByAccount("acct-empty") } returns 0

        assertThat(useCase("acct-empty")).isEqualTo(0)
    }
}
