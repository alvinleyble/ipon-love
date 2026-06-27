package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.domain.usecase.TransactionValidator
import org.junit.Test
import java.math.BigDecimal

class TransactionValidatorTest {

    private fun validate(
        type: TransactionType,
        amount: String = "100.00",
        accountId: String? = "acc-1",
        toAccountId: String? = null,
        categoryId: String? = "cat-1",
        isPrivate: Boolean = false,
        touchesSharedAccount: Boolean = false,
    ) = TransactionValidator.validate(
        type, BigDecimal(amount), accountId, toAccountId, categoryId,
        isPrivate = isPrivate,
        touchesSharedAccount = touchesSharedAccount,
    )

    @Test
    fun validExpense_hasNoErrors() {
        assertThat(validate(TransactionType.EXPENSE)).isEmpty()
    }

    @Test
    fun validIncome_hasNoErrors() {
        assertThat(validate(TransactionType.INCOME)).isEmpty()
    }

    @Test
    fun validTransfer_hasNoErrors() {
        val errors = validate(
            type = TransactionType.TRANSFER,
            toAccountId = "acc-2",
            categoryId = null,
        )
        assertThat(errors).isEmpty()
    }

    @Test
    fun zeroAmount_isNotPositive() {
        assertThat(validate(TransactionType.EXPENSE, amount = "0"))
            .contains(TransactionError.AMOUNT_NOT_POSITIVE)
    }

    @Test
    fun negativeAmount_isNotPositive() {
        assertThat(validate(TransactionType.EXPENSE, amount = "-5.00"))
            .contains(TransactionError.AMOUNT_NOT_POSITIVE)
    }

    @Test
    fun blankAccount_isRequired() {
        assertThat(validate(TransactionType.EXPENSE, accountId = " "))
            .contains(TransactionError.ACCOUNT_REQUIRED)
    }

    @Test
    fun incomeOrExpense_withoutCategory_requiresCategory() {
        assertThat(validate(TransactionType.EXPENSE, categoryId = null))
            .contains(TransactionError.CATEGORY_REQUIRED)
        assertThat(validate(TransactionType.INCOME, categoryId = null))
            .contains(TransactionError.CATEGORY_REQUIRED)
    }

    @Test
    fun transfer_withoutDestination_requiresDestination() {
        val errors = validate(TransactionType.TRANSFER, toAccountId = null, categoryId = null)
        assertThat(errors).contains(TransactionError.DESTINATION_REQUIRED)
    }

    @Test
    fun transfer_toSameAccount_isRejected() {
        val errors = validate(
            type = TransactionType.TRANSFER,
            accountId = "acc-1",
            toAccountId = "acc-1",
            categoryId = null,
        )
        assertThat(errors).contains(TransactionError.DESTINATION_SAME_AS_SOURCE)
    }

    @Test
    fun transfer_doesNotRequireCategory() {
        val errors = validate(TransactionType.TRANSFER, toAccountId = "acc-2", categoryId = null)
        assertThat(errors).doesNotContain(TransactionError.CATEGORY_REQUIRED)
    }

    @Test
    fun privateSpend_onSharedAccount_isRejected() {
        // ADR-0018: a shared account's spend must stay non-private so the joint balance is
        // computable and identical on both devices.
        val errors = validate(TransactionType.EXPENSE, isPrivate = true, touchesSharedAccount = true)
        assertThat(errors).contains(TransactionError.PRIVATE_ON_SHARED_ACCOUNT)
    }

    @Test
    fun nonPrivateSpend_onSharedAccount_isAllowed() {
        val errors = validate(TransactionType.EXPENSE, isPrivate = false, touchesSharedAccount = true)
        assertThat(errors).doesNotContain(TransactionError.PRIVATE_ON_SHARED_ACCOUNT)
    }

    @Test
    fun privateSpend_onPersonalAccount_isAllowed() {
        val errors = validate(TransactionType.EXPENSE, isPrivate = true, touchesSharedAccount = false)
        assertThat(errors).doesNotContain(TransactionError.PRIVATE_ON_SHARED_ACCOUNT)
    }
}
