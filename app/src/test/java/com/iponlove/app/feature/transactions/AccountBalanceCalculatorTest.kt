package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import org.junit.Test
import java.math.BigDecimal

/** ADR-0007: balance = opening_balance + ledger. The core money math. */
class AccountBalanceCalculatorTest {

    private fun bd(value: String) = BigDecimal(value)

    @Test
    fun noTransactions_returnsOpeningBalance() {
        val balance = AccountBalanceCalculator.balanceOf("acc-1", bd("500.00"), emptyList())
        assertThat(balance).isEqualTo(bd("500.00"))
    }

    @Test
    fun income_addsToAccount() {
        val txns = listOf(txn("t1", TransactionType.INCOME, "150.00", accountId = "acc-1"))
        val balance = AccountBalanceCalculator.balanceOf("acc-1", bd("500.00"), txns)
        assertThat(balance).isEqualTo(bd("650.00"))
    }

    @Test
    fun expense_subtractsFromAccount() {
        val txns = listOf(txn("t1", TransactionType.EXPENSE, "200.00", accountId = "acc-1"))
        val balance = AccountBalanceCalculator.balanceOf("acc-1", bd("500.00"), txns)
        assertThat(balance).isEqualTo(bd("300.00"))
    }

    @Test
    fun settlementLegs_stillMoveBalance() {
        // Unlike Analysis, balance derivation counts settlement legs — money really moved (ADR-0019 #14).
        val txns = listOf(
            txn("settle-out", TransactionType.EXPENSE, "200.00", accountId = "acc-1", categoryId = null, isSettlement = true),
            txn("settle-in", TransactionType.INCOME, "50.00", accountId = "acc-1", categoryId = null, isSettlement = true),
        )
        val balance = AccountBalanceCalculator.balanceOf("acc-1", bd("500.00"), txns)
        assertThat(balance).isEqualTo(bd("350.00"))
    }

    @Test
    fun transfer_movesBetweenSourceAndDestination() {
        val txns = listOf(
            txn("t1", TransactionType.TRANSFER, "100.00", accountId = "acc-1", toAccountId = "acc-2"),
        )
        val opening = mapOf("acc-1" to bd("500.00"), "acc-2" to bd("50.00"))

        val balances = AccountBalanceCalculator.balances(opening, txns)

        assertThat(balances.getValue("acc-1")).isEqualTo(bd("400.00"))
        assertThat(balances.getValue("acc-2")).isEqualTo(bd("150.00"))
    }

    @Test
    fun transferFee_isJustAnotherExpenseOnTheSourceAccount() {
        // ADR-0031: the linked fee expense carries no special flag — the calculator needs no
        // new code path, it's real spend like any other EXPENSE row.
        val txns = listOf(
            txn("t1", TransactionType.TRANSFER, "100.00", accountId = "acc-1", toAccountId = "acc-2"),
            txn("fee-1", TransactionType.EXPENSE, "5.00", accountId = "acc-1", categoryId = "fee-cat"),
        )
        val opening = mapOf("acc-1" to bd("500.00"), "acc-2" to bd("50.00"))

        val balances = AccountBalanceCalculator.balances(opening, txns)

        assertThat(balances.getValue("acc-1")).isEqualTo(bd("395.00")) // 500 - 100 - 5
        assertThat(balances.getValue("acc-2")).isEqualTo(bd("150.00"))
    }

    @Test
    fun unrelatedAccount_isUnaffected() {
        val txns = listOf(txn("t1", TransactionType.EXPENSE, "200.00", accountId = "acc-1"))
        val balance = AccountBalanceCalculator.balanceOf("acc-2", bd("999.00"), txns)
        assertThat(balance).isEqualTo(bd("999.00"))
    }

    @Test
    fun postingsToUnknownAccounts_areIgnored() {
        // A transfer touching an account we don't hold opening balances for must not crash
        // or invent a balance for it.
        val txns = listOf(
            txn("t1", TransactionType.TRANSFER, "100.00", accountId = "acc-1", toAccountId = "partner-acc"),
        )
        val balances = AccountBalanceCalculator.balances(mapOf("acc-1" to bd("500.00")), txns)

        assertThat(balances.keys).containsExactly("acc-1")
        assertThat(balances.getValue("acc-1")).isEqualTo(bd("400.00"))
    }

    @Test
    fun sharedAccount_sumsBothPartnersPostings() {
        // ADR-0018: a couple-owned account's balance is opening + BOTH members' postings. The
        // calculator is owner-agnostic, so the balance ledger (own txns + every member's
        // non-private ones) feeds it postings from both partners against the same shared id.
        val txns = listOf(
            txn("mine", TransactionType.EXPENSE, "200.00", accountId = "shared-1"),     // me
            txn("partner", TransactionType.EXPENSE, "150.00", accountId = "shared-1"),  // partner
            txn("myIncome", TransactionType.INCOME, "1000.00", accountId = "shared-1"), // me
        )
        val balance = AccountBalanceCalculator.balanceOf("shared-1", bd("500.00"), txns)
        // 500 + 1000 - 200 - 150 = 1150.00
        assertThat(balance).isEqualTo(bd("1150.00"))
    }

    @Test
    fun multipleTransactions_accumulate() {
        val txns = listOf(
            txn("t1", TransactionType.INCOME, "1000.00", accountId = "acc-1"),
            txn("t2", TransactionType.EXPENSE, "250.50", accountId = "acc-1"),
            txn("t3", TransactionType.EXPENSE, "49.50", accountId = "acc-1"),
            txn("t4", TransactionType.TRANSFER, "200.00", accountId = "acc-1", toAccountId = "acc-2"),
        )
        val opening = mapOf("acc-1" to bd("100.00"), "acc-2" to bd("0.00"))

        val balances = AccountBalanceCalculator.balances(opening, txns)

        // 100 + 1000 - 250.50 - 49.50 - 200 = 600.00
        assertThat(balances.getValue("acc-1")).isEqualTo(bd("600.00"))
        assertThat(balances.getValue("acc-2")).isEqualTo(bd("200.00"))
    }
}
