package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.presentation.TransactionEditorReducer
import com.iponlove.app.feature.transactions.presentation.TransactionEditorReducer.BuildResult
import com.iponlove.app.feature.transactions.presentation.TransactionEditorState
import org.junit.Test
import java.math.BigDecimal

class TransactionEditorReducerTest {

    private fun draft(
        type: TransactionType = TransactionType.EXPENSE,
        amountText: String = "100",
        accountId: String? = "acc-1",
        toAccountId: String? = null,
        categoryId: String? = "cat-1",
        isPrivate: Boolean = false,
        paidForPartner: Boolean = false,
        amountOwedText: String = "",
        transferFeeText: String = "",
        isAdjustment: Boolean = false,
        isSettlement: Boolean = false,
    ) = TransactionEditorState(
        id = "txn-1",
        type = type,
        amountText = amountText,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        isPrivate = isPrivate,
        paidForPartner = paidForPartner,
        amountOwedText = amountOwedText,
        transferFeeText = transferFeeText,
        isAdjustment = isAdjustment,
        isSettlement = isSettlement,
    )

    @Test
    fun onType_toTransfer_clearsCategoryKeepsToAccount() {
        val start = draft(categoryId = "cat-1", toAccountId = "acc-2")
        val result = TransactionEditorReducer.onType(start, TransactionType.TRANSFER)
        assertThat(result.categoryId).isNull()
        assertThat(result.toAccountId).isEqualTo("acc-2")
    }

    @Test
    fun onType_awayFromExpense_clearsPaidForPartner() {
        val start = draft(paidForPartner = true)
        val result = TransactionEditorReducer.onType(start, TransactionType.INCOME)
        assertThat(result.paidForPartner).isFalse()
    }

    @Test
    fun onType_awayFromTransfer_clearsTransferFeeText() {
        val start = draft(type = TransactionType.TRANSFER, transferFeeText = "50")
        val result = TransactionEditorReducer.onType(start, TransactionType.EXPENSE)
        assertThat(result.transferFeeText).isEmpty()
    }

    @Test
    fun onType_toTransfer_keepsTransferFeeText() {
        val start = draft(type = TransactionType.EXPENSE, transferFeeText = "")
        val withFee = start.copy(transferFeeText = "50")
        val result = TransactionEditorReducer.onType(withFee, TransactionType.TRANSFER)
        assertThat(result.transferFeeText).isEqualTo("50")
    }

    @Test
    fun onTransferFee_updatesTextAndClearsError() {
        val start = draft().copy(transferFeeError = true)
        val result = TransactionEditorReducer.onTransferFee(start, "25.00")
        assertThat(result.transferFeeText).isEqualTo("25.00")
        assertThat(result.transferFeeError).isFalse()
    }

    @Test
    fun onAccount_shared_clearsPrivate() {
        val start = draft(isPrivate = true)
        val result = TransactionEditorReducer.onAccount(start, "acc-shared", setOf("acc-shared"))
        assertThat(result.isPrivate).isFalse()
    }

    @Test
    fun onAccount_nonShared_keepsPrivate() {
        val start = draft(isPrivate = true)
        val result = TransactionEditorReducer.onAccount(start, "acc-9", setOf("acc-shared"))
        assertThat(result.isPrivate).isTrue()
    }

    @Test
    fun onPaidForPartner_on_defaultsOwedToAmount() {
        val start = draft(amountText = "250", amountOwedText = "")
        val result = TransactionEditorReducer.onPaidForPartner(start, true)
        assertThat(result.amountOwedText).isEqualTo("250")
    }

    @Test
    fun build_validExpense_isReadyWithNoDebt() {
        val result = TransactionEditorReducer.build(draft(), emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).amountOwed).isNull()
        assertThat(result.transaction.amount).isEqualTo(BigDecimal("100"))
    }

    @Test
    fun build_adjustmentRow_roundTripsFlagAndSkipsCategoryRequirement() {
        // Mirrors v1.7.1 Item 15's isSettlement bug fix: re-saving a loaded adjustment row must
        // not silently strip the flag, and the categoryless row must not fail validation.
        val start = draft(categoryId = null, isAdjustment = true)
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).transaction.isAdjustment).isTrue()
    }

    @Test
    fun build_settlementRow_roundTripsFlagAndSkipsCategoryRequirement() {
        // v1.7.1 Item 15: editing a debt-settlement leg used to silently strip isSettlement,
        // reclassifying it as real spend/income in Analysis, Budgets, and Combined.
        val start = draft(categoryId = null, isSettlement = true)
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).transaction.isSettlement).isTrue()
    }

    @Test
    fun build_zeroAmount_isInvalid() {
        val result = TransactionEditorReducer.build(draft(amountText = "0"), emptySet(), false)
        assertThat(result).isInstanceOf(BuildResult.Invalid::class.java)
        assertThat((result as BuildResult.Invalid).errors)
            .contains(TransactionError.AMOUNT_NOT_POSITIVE)
    }

    @Test
    fun build_paidForPartner_blankOwed_usesFullAmount() {
        val start = draft(amountText = "300", paidForPartner = true, amountOwedText = "")
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = true)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).amountOwed).isEqualTo(BigDecimal("300"))
    }

    @Test
    fun build_paidForPartner_owedExceedsAmount_isOwedInvalid() {
        val start = draft(amountText = "100", paidForPartner = true, amountOwedText = "150")
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = true)
        assertThat(result).isEqualTo(BuildResult.OwedInvalid)
    }

    @Test
    fun build_paidForPartner_butNotAllowed_savesPlainTransaction() {
        val start = draft(paidForPartner = true, amountOwedText = "50")
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).amountOwed).isNull()
    }

    @Test
    fun build_transfer_blankFee_isReadyWithZeroFee() {
        val start = draft(
            type = TransactionType.TRANSFER,
            toAccountId = "acc-2",
            categoryId = null,
            transferFeeText = "",
        )
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).transferFee).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun build_transfer_withFee_isReadyWithThatFee() {
        val start = draft(
            type = TransactionType.TRANSFER,
            toAccountId = "acc-2",
            categoryId = null,
            transferFeeText = "15.50",
        )
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isInstanceOf(BuildResult.Ready::class.java)
        assertThat((result as BuildResult.Ready).transferFee).isEqualTo(BigDecimal("15.50"))
    }

    @Test
    fun build_transfer_negativeFee_isTransferFeeInvalid() {
        val start = draft(
            type = TransactionType.TRANSFER,
            toAccountId = "acc-2",
            categoryId = null,
            transferFeeText = "-5.00",
        )
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isEqualTo(BuildResult.TransferFeeInvalid)
    }

    @Test
    fun build_transfer_garbageFeeText_isTransferFeeInvalid() {
        val start = draft(
            type = TransactionType.TRANSFER,
            toAccountId = "acc-2",
            categoryId = null,
            transferFeeText = "abc",
        )
        val result = TransactionEditorReducer.build(start, emptySet(), canPayForPartner = false)
        assertThat(result).isEqualTo(BuildResult.TransferFeeInvalid)
    }
}
