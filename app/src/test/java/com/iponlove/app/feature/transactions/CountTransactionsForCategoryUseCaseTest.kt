package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.transactions.domain.usecase.CountTransactionsForCategoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The count that drives the category delete-confirm's "Used by N transactions" copy + archive-steer
 * (v1.6.7 Item 5). The SQL itself (an active-rows COUNT on `categoryId`) is verified on-device — the
 * project has no JVM Room harness — so this locks the domain wiring: the use case delegates to
 * [TransactionRepository.countByCategory] with the requested id and returns its result verbatim.
 */
class CountTransactionsForCategoryUseCaseTest {

    private val repository = mockk<TransactionRepository>()
    private val useCase = CountTransactionsForCategoryUseCase(repository)

    @Test
    fun invoke_returnsRepositoryCountForTheGivenCategory() = runTest {
        coEvery { repository.countByCategory("cat-1") } returns 7

        val count = useCase("cat-1")

        assertThat(count).isEqualTo(7)
        coVerify(exactly = 1) { repository.countByCategory("cat-1") }
    }

    @Test
    fun invoke_zeroWhenNoTransactionsReferenceTheCategory() = runTest {
        coEvery { repository.countByCategory("cat-empty") } returns 0

        assertThat(useCase("cat-empty")).isEqualTo(0)
    }
}
