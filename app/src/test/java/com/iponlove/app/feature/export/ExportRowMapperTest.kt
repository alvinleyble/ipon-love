package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRowMapper
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Pure-function tests for the [ExportRowMapper] (v1.7.0 Item 6, Slice 1): name resolution, the
 * category-less label branches (transfer → destination, settlement → "Debt settlement"), and
 * sign-by-type. Mirrors the Records `toListItem` label rules.
 */
class ExportRowMapperTest {

    private val accountNames = mapOf("bank" to "BPI", "card" to "Credit Card")
    private val categoryNames = mapOf("food" to "Groceries")

    private fun txn(
        type: TransactionType,
        amount: String,
        accountId: String = "bank",
        toAccountId: String? = null,
        categoryId: String? = null,
        note: String? = null,
        isSettlement: Boolean = false,
        isAdjustment: Boolean = false,
        isPrivate: Boolean = false,
    ) = Transaction(
        id = "t",
        type = type,
        amount = BigDecimal(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        note = note,
        date = Instant.parse("2026-07-20T02:00:00Z"),
        isSettlement = isSettlement,
        isAdjustment = isAdjustment,
        isPrivate = isPrivate,
    )

    @Test
    fun `income keeps a positive amount, expense negates`() {
        val income = ExportRowMapper.toRow(
            txn(TransactionType.INCOME, "20000.00", categoryId = "food"), accountNames, categoryNames,
        )
        val expense = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "500.00", categoryId = "food"), accountNames, categoryNames,
        )
        assertThat(income.signedAmount).isEqualTo(BigDecimal("20000.00"))
        assertThat(expense.signedAmount).isEqualTo(BigDecimal("-500.00"))
    }

    @Test
    fun `transfer resolves the destination account and negates`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.TRANSFER, "800.00", accountId = "bank", toAccountId = "card"),
            accountNames, categoryNames,
        )
        assertThat(row.category).isEqualTo("→ Credit Card")
        assertThat(row.account).isEqualTo("BPI")
        assertThat(row.signedAmount).isEqualTo(BigDecimal("-800.00"))
    }

    @Test
    fun `settlement leg is labelled and never uncategorized`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "300.00", categoryId = null, isSettlement = true),
            accountNames, categoryNames,
        )
        assertThat(row.category).isEqualTo("Debt settlement")
    }

    @Test
    fun `balance-adjustment row is labelled and never uncategorized`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.INCOME, "500.00", categoryId = null, isAdjustment = true),
            accountNames, categoryNames,
        )
        assertThat(row.category).isEqualTo("Balance adjustment")
    }

    @Test
    fun `a categorised row with an unknown category falls back to Uncategorized`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "100.00", categoryId = "ghost"), accountNames, categoryNames,
        )
        assertThat(row.category).isEqualTo("Uncategorized")
    }

    @Test
    fun `private rows are exported like any other`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "100.00", categoryId = "food", isPrivate = true, note = "secret"),
            accountNames, categoryNames,
        )
        assertThat(row.category).isEqualTo("Groceries")
        assertThat(row.note).isEqualTo("secret")
        assertThat(row.signedAmount).isEqualTo(BigDecimal("-100.00"))
    }

    @Test
    fun `receipt photos are carried through and counted`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "100.00", categoryId = "food"),
            accountNames,
            categoryNames,
            receipts = List(3) { ExportPhoto(id = "img$it", url = "https://example/$it.jpg") },
        )
        assertThat(row.receiptCount).isEqualTo(3)
        assertThat(row.receipts.map { it.id }).containsExactly("img0", "img1", "img2").inOrder()
    }

    @Test
    fun `a row with no photos counts zero`() {
        val row = ExportRowMapper.toRow(
            txn(TransactionType.EXPENSE, "100.00", categoryId = "food"), accountNames, categoryNames,
        )
        assertThat(row.receiptCount).isEqualTo(0)
    }
}
