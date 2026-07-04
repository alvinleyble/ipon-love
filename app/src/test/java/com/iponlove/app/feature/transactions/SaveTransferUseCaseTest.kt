package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.categories.FakeCategoryDao
import com.iponlove.app.feature.categories.data.CategoryRepositoryImpl
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransferUseCase
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * [SaveTransferUseCase] wired to real repositories over fake DAOs (ADR-0031): a non-zero
 * fee becomes a linked "Transfer fees" expense; every save retires the old linked row (if
 * any) and mints a fresh one rather than editing in place, so a cleared/changed fee never
 * leaves a stale amount behind.
 */
class SaveTransferUseCaseTest {

    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(10_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val transactionRepository = TransactionRepositoryImpl(transactionDao, clock, currentUser)
    private val categoryRepository = CategoryRepositoryImpl(categoryDao, clock, currentUser)
    private val useCase = SaveTransferUseCase(
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        currentUser = currentUser,
        upsertTransaction = UpsertTransactionUseCase(transactionRepository),
    )

    private fun transfer(fee: String? = null) = txn(
        "transfer-1",
        TransactionType.TRANSFER,
        "1000.00",
        accountId = "acc-1",
        toAccountId = "acc-2",
    ).copy(transferFeeTransactionId = fee)

    @Test
    fun nonZeroFee_createsLinkedExpense_underAutoCreatedCategory() = runTest {
        useCase(transfer(), BigDecimal("50.00"))

        val transferRow = transactionDao.store.getValue("transfer-1")
        val feeId = requireNotNull(transferRow.transferFeeTransactionId)
        val feeRow = transactionDao.store.getValue(feeId)

        assertThat(feeRow.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(feeRow.amount.toPlainString()).isEqualTo("50.00")
        assertThat(feeRow.accountId).isEqualTo("acc-1") // deducted from the transfer's source
        assertThat(feeRow.isDeleted).isFalse()

        val category = categoryDao.store.getValue(feeRow.categoryId!!)
        assertThat(category.name).isEqualTo("Transfer fees")
    }

    @Test
    fun zeroFee_savesPlainTransfer_withNoLink() = runTest {
        useCase(transfer(), BigDecimal.ZERO)

        val transferRow = transactionDao.store.getValue("transfer-1")
        assertThat(transferRow.transferFeeTransactionId).isNull()
        assertThat(transactionDao.store).hasSize(1) // only the transfer, no fee row
    }

    @Test
    fun blankFeeText_neverWritten_reusesSameBuiltInCategoryAcrossUsers() = runTest {
        useCase(transfer(), BigDecimal("10.00"))
        val firstCategoryId = transactionDao.store.getValue(
            transactionDao.store.getValue("transfer-1").transferFeeTransactionId!!,
        ).categoryId

        // A second, unrelated transfer's fee should resolve to the exact same category id.
        useCase(
            txn("transfer-2", TransactionType.TRANSFER, "500.00", accountId = "acc-1", toAccountId = "acc-2"),
            BigDecimal("5.00"),
        )
        val secondCategoryId = transactionDao.store.getValue(
            transactionDao.store.getValue("transfer-2").transferFeeTransactionId!!,
        ).categoryId

        assertThat(secondCategoryId).isEqualTo(firstCategoryId)
        assertThat(categoryDao.store).hasSize(1) // idempotent — not re-created per transfer
    }

    @Test
    fun editingFeeAmount_retiresOldLinkedExpense_createsFreshOne() = runTest {
        useCase(transfer(), BigDecimal("50.00"))
        val oldFeeId = transactionDao.store.getValue("transfer-1").transferFeeTransactionId!!

        useCase(transfer(fee = oldFeeId), BigDecimal("75.00"))
        val newFeeId = transactionDao.store.getValue("transfer-1").transferFeeTransactionId!!

        assertThat(newFeeId).isNotEqualTo(oldFeeId)
        assertThat(transactionDao.store.getValue(oldFeeId).isDeleted).isTrue()
        val newFeeRow = transactionDao.store.getValue(newFeeId)
        assertThat(newFeeRow.isDeleted).isFalse()
        assertThat(newFeeRow.amount.toPlainString()).isEqualTo("75.00")
    }

    @Test
    fun clearingFeeToZero_retiresLinkedExpense_andClearsPointer() = runTest {
        useCase(transfer(), BigDecimal("50.00"))
        val oldFeeId = transactionDao.store.getValue("transfer-1").transferFeeTransactionId!!

        useCase(transfer(fee = oldFeeId), BigDecimal.ZERO)

        assertThat(transactionDao.store.getValue("transfer-1").transferFeeTransactionId).isNull()
        assertThat(transactionDao.store.getValue(oldFeeId).isDeleted).isTrue()
    }

    @Test
    fun negativeFee_throws_andWritesNothing() = runTest {
        val error = runCatching { useCase(transfer(), BigDecimal("-1.00")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(transactionDao.store).isEmpty()
    }

    @Test
    fun nonTransferType_throws() = runTest {
        val expense = txn("t1", TransactionType.EXPENSE, "100.00")

        val error = runCatching { useCase(expense, BigDecimal("10.00")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
