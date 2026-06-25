package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CheckBudgetAlertsUseCaseTest {

    private val useCase = CheckBudgetAlertsUseCase()
    private val zone = ZoneOffset.UTC
    private val month = "2026-06"
    private val juneInstant = Instant.parse("2026-06-15T00:00:00Z")

    private fun expense(id: String, amount: String, categoryId: String? = "cat-1") =
        txn(id, TransactionType.EXPENSE, amount, categoryId = categoryId, date = juneInstant)

    private fun income(id: String, amount: String) =
        txn(id, TransactionType.INCOME, amount, date = juneInstant)

    @Test
    fun `no alert below 80 percent`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "7900.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `fires 80 percent alert at exactly 80`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "8000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).hasSize(1)
        assertThat(result[0].threshold).isEqualTo(80)
    }

    @Test
    fun `fires both 80 and 100 alerts at 100 percent`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.threshold }).containsExactly(80, 100)
    }

    @Test
    fun `does not re-fire already-fired thresholds`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val alreadyFired = setOf(
            CheckBudgetAlertsUseCase.dedupeKey("b1", month, 80),
            CheckBudgetAlertsUseCase.dedupeKey("b1", month, 100),
        )
        val result = useCase(listOf(budget), txns, alreadyFired, month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `fires 80 when only 100 already deduped`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val alreadyFired = setOf(CheckBudgetAlertsUseCase.dedupeKey("b1", month, 100))
        val result = useCase(listOf(budget), txns, alreadyFired, month, zone)
        assertThat(result.map { it.threshold }).containsExactly(80)
    }

    @Test
    fun `income transactions do not count toward budget`() {
        val budget = budget("b1", amount = "5000.00", yearMonth = month)
        val txns = listOf(income("t1", "9000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `category budget only counts matching category expenses`() {
        val budget = budget("b1", categoryId = "cat-food", amount = "3000.00", yearMonth = month)
        val txns = listOf(
            expense("t1", "2000.00", categoryId = "cat-food"),
            expense("t2", "5000.00", categoryId = "cat-rent"),
        )
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).isEmpty() // 2000/3000 = 66% → below 80
    }

    @Test
    fun `category budget fires at 80 for matching category`() {
        val budget = budget("b1", categoryId = "cat-food", amount = "3000.00", yearMonth = month)
        val txns = listOf(expense("t1", "2500.00", categoryId = "cat-food"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.threshold }).containsExactly(80)
    }

    @Test
    fun `overall budget counts all expense categories`() {
        val budget = budget("b1", categoryId = null, amount = "5000.00", yearMonth = month)
        val txns = listOf(
            expense("t1", "2000.00", categoryId = "cat-food"),
            expense("t2", "2000.00", categoryId = "cat-rent"),
        )
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.threshold }).containsExactly(80)
    }

    @Test
    fun `skips budget for a different month`() {
        val budget = budget("b1", amount = "1000.00", yearMonth = "2026-05")
        val txns = listOf(expense("t1", "1000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `skips budget with zero amount`() {
        val budget = budget("b1", amount = "0.00", yearMonth = month)
        val txns = listOf(expense("t1", "1.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `dedupeKey format is stable`() {
        assertThat(CheckBudgetAlertsUseCase.dedupeKey("abc", "2026-06", 80))
            .isEqualTo("abc:2026-06:80")
    }
}
