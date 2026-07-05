package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.presentation.toListItem
import org.junit.Test

/** ADR-0042: settlement legs (categoryId = null + isSettlement) render as "Debt settlement". */
class TransactionsListItemMapperTest {

    private val accountNames = mapOf("acc-1" to "Wallet", "acc-2" to "Bank")
    private val categoryNames = mapOf("cat-1" to "Groceries")

    @Test
    fun `settlement expense leg titles as Debt settlement`() {
        val item = txn(
            id = "t1",
            type = TransactionType.EXPENSE,
            amount = "500.00",
            categoryId = null,
            isSettlement = true,
        ).toListItem(accountNames, categoryNames)

        assertThat(item.title).isEqualTo("Debt settlement")
    }

    @Test
    fun `settlement income leg titles as Debt settlement`() {
        val item = txn(
            id = "t2",
            type = TransactionType.INCOME,
            amount = "500.00",
            categoryId = null,
            isSettlement = true,
        ).toListItem(accountNames, categoryNames)

        assertThat(item.title).isEqualTo("Debt settlement")
    }

    @Test
    fun `normal category-less expense still reads Uncategorized`() {
        val item = txn(
            id = "t3",
            type = TransactionType.EXPENSE,
            amount = "100.00",
            categoryId = null,
            isSettlement = false,
        ).toListItem(accountNames, categoryNames)

        assertThat(item.title).isEqualTo("Uncategorized")
    }

    @Test
    fun `categorized expense reads its category name`() {
        val item = txn(
            id = "t4",
            type = TransactionType.EXPENSE,
            amount = "100.00",
            categoryId = "cat-1",
        ).toListItem(accountNames, categoryNames)

        assertThat(item.title).isEqualTo("Groceries")
    }

    @Test
    fun `transfer titles as Transfer regardless of settlement flag`() {
        val item = txn(
            id = "t5",
            type = TransactionType.TRANSFER,
            amount = "100.00",
            accountId = "acc-1",
            toAccountId = "acc-2",
        ).toListItem(accountNames, categoryNames)

        assertThat(item.title).isEqualTo("Transfer")
        assertThat(item.subtitle).isEqualTo("Wallet → Bank")
    }
}
