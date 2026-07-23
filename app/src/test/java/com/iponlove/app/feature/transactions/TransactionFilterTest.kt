package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure-function tests for the Records filter (v1.7.0 Item 7). Covers the deliberate,
 * non-obvious semantics from the grill: category never matches category-less rows, account
 * spans both legs of a transfer, amount bounds are inclusive, AND-across / OR-within.
 */
class TransactionFilterTest {

    private val groceries = txn("t1", TransactionType.EXPENSE, "500.00", accountId = "bank", categoryId = "food")
    private val salary = txn("t2", TransactionType.INCOME, "20000.00", accountId = "bank", categoryId = "pay")
    private val shopping = txn("t3", TransactionType.EXPENSE, "1200.00", accountId = "card", categoryId = "shop")
    private val paydown = txn("t4", TransactionType.TRANSFER, "800.00", accountId = "bank", toAccountId = "card")
    private val settlement = txn("t5", TransactionType.EXPENSE, "300.00", accountId = "cash", categoryId = null, isSettlement = true)

    private val all = listOf(groceries, salary, shopping, paydown, settlement)

    @Test
    fun `NONE returns input unchanged`() {
        assertThat(TransactionFilter.NONE.apply(all)).isEqualTo(all)
    }

    @Test
    fun `category filter matches selected and excludes others`() {
        val result = TransactionFilter(categoryIds = setOf("food")).apply(all)
        assertThat(result).containsExactly(groceries)
    }

    @Test
    fun `category filter hides transfers and settlements`() {
        // Both carry categoryId = null, so a category constraint can never match them.
        val result = TransactionFilter(categoryIds = setOf("food", "shop")).apply(all)
        assertThat(result).doesNotContain(paydown)
        assertThat(result).doesNotContain(settlement)
    }

    @Test
    fun `account filter matches on accountId`() {
        val result = TransactionFilter(accountIds = setOf("card")).apply(all)
        // shopping (accountId=card) and paydown (toAccountId=card) both touch the card.
        assertThat(result).containsExactly(shopping, paydown)
    }

    @Test
    fun `account filter matches a transfer on toAccountId`() {
        val result = TransactionFilter(accountIds = setOf("card")).apply(listOf(paydown))
        assertThat(result).containsExactly(paydown)
    }

    @Test
    fun `settlement matches an account filter on accountId`() {
        val result = TransactionFilter(accountIds = setOf("cash")).apply(all)
        assertThat(result).containsExactly(settlement)
    }

    @Test
    fun `type filter matches selected type`() {
        val result = TransactionFilter(types = setOf(TransactionType.INCOME)).apply(all)
        assertThat(result).containsExactly(salary)
    }

    @Test
    fun `amount min only is inclusive at the boundary`() {
        val result = TransactionFilter(minAmount = BigDecimal("500.00")).apply(all)
        // 500, 20000, 1200, 800 pass; the 300 settlement is below.
        assertThat(result).containsExactly(groceries, salary, shopping, paydown)
    }

    @Test
    fun `amount max only is inclusive at the boundary`() {
        val result = TransactionFilter(maxAmount = BigDecimal("800.00")).apply(all)
        // 500, 800, 300 pass; 20000 and 1200 are above.
        assertThat(result).containsExactly(groceries, paydown, settlement)
    }

    @Test
    fun `amount range clips at both inclusive boundaries`() {
        val result = TransactionFilter(
            minAmount = BigDecimal("500.00"),
            maxAmount = BigDecimal("1200.00"),
        ).apply(all)
        assertThat(result).containsExactly(groceries, shopping, paydown)
    }

    @Test
    fun `OR within a dimension returns rows from both selections`() {
        val result = TransactionFilter(categoryIds = setOf("food", "shop")).apply(all)
        assertThat(result).containsExactly(groceries, shopping)
    }

    @Test
    fun `AND across dimensions intersects, not unions`() {
        val result = TransactionFilter(
            categoryIds = setOf("food", "shop"),
            accountIds = setOf("card"),
        ).apply(all)
        // Only shopping is both a food/shop category AND on the card.
        assertThat(result).containsExactly(shopping)
    }

    @Test
    fun `parseBound treats blank and unparseable as null`() {
        assertThat(TransactionFilter.parseBound("")).isNull()
        assertThat(TransactionFilter.parseBound("   ")).isNull()
        assertThat(TransactionFilter.parseBound("abc")).isNull()
        assertThat(TransactionFilter.parseBound("500")).isEqualTo(BigDecimal("500"))
        assertThat(TransactionFilter.parseBound(" 12.50 ")).isEqualTo(BigDecimal("12.50"))
    }

    @Test
    fun `inverted range is flagged invalid`() {
        val inverted = TransactionFilter(
            minAmount = BigDecimal("1000"),
            maxAmount = BigDecimal("100"),
        )
        assertThat(inverted.isValid).isFalse()

        val ordered = TransactionFilter(
            minAmount = BigDecimal("100"),
            maxAmount = BigDecimal("1000"),
        )
        assertThat(ordered.isValid).isTrue()

        // A single bound (or none) is always valid.
        assertThat(TransactionFilter(minAmount = BigDecimal("100")).isValid).isTrue()
        assertThat(TransactionFilter.NONE.isValid).isTrue()
    }

    @Test
    fun `isActive reflects any constraint`() {
        assertThat(TransactionFilter.NONE.isActive).isFalse()
        assertThat(TransactionFilter(types = setOf(TransactionType.EXPENSE)).isActive).isTrue()
        assertThat(TransactionFilter(minAmount = BigDecimal.ONE).isActive).isTrue()
    }
}
