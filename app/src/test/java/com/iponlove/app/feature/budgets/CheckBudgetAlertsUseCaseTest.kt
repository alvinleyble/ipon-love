package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.BudgetAlertSlot
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
    fun `fires warn rung at exactly 80`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "8000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result).hasSize(1)
        assertThat(result[0].slot).isEqualTo(BudgetAlertSlot.WARN)
    }

    @Test
    fun `fires both warn and limit rungs at 100 percent`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.slot })
            .containsExactly(BudgetAlertSlot.WARN, BudgetAlertSlot.LIMIT)
            .inOrder()
    }

    @Test
    fun `does not re-fire rungs already raised in the inbox`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val alreadyRaised = setOf(
            CheckBudgetAlertsUseCase.notificationId("b1", month, BudgetAlertSlot.WARN),
            CheckBudgetAlertsUseCase.notificationId("b1", month, BudgetAlertSlot.LIMIT),
        )
        val result = useCase(listOf(budget), txns, alreadyRaised, month, zone)
        assertThat(result).isEmpty()
    }

    @Test
    fun `fires warn when only limit already raised`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val alreadyRaised =
            setOf(CheckBudgetAlertsUseCase.notificationId("b1", month, BudgetAlertSlot.LIMIT))
        val result = useCase(listOf(budget), txns, alreadyRaised, month, zone)
        assertThat(result.map { it.slot }).containsExactly(BudgetAlertSlot.WARN)
    }

    /**
     * The month is part of the id, so a new month re-arms every rung automatically — this is
     * what let the old BudgetAlertStore's explicit month-clear go away (ADR-0053).
     */
    @Test
    fun `last month's raised ids do not suppress this month's rungs`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "8000.00"))
        val lastMonth = setOf(
            CheckBudgetAlertsUseCase.notificationId("b1", "2026-05", BudgetAlertSlot.WARN),
        )
        val result = useCase(listOf(budget), txns, lastMonth, month, zone)
        assertThat(result.map { it.slot }).containsExactly(BudgetAlertSlot.WARN)
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
    fun `category budget fires warn for matching category`() {
        val budget = budget("b1", categoryId = "cat-food", amount = "3000.00", yearMonth = month)
        val txns = listOf(expense("t1", "2500.00", categoryId = "cat-food"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.slot }).containsExactly(BudgetAlertSlot.WARN)
    }

    @Test
    fun `overall budget counts all expense categories`() {
        val budget = budget("b1", categoryId = null, amount = "5000.00", yearMonth = month)
        val txns = listOf(
            expense("t1", "2000.00", categoryId = "cat-food"),
            expense("t2", "2000.00", categoryId = "cat-rent"),
        )
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.slot }).containsExactly(BudgetAlertSlot.WARN)
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

    /**
     * The id shape is a synced contract (two clients raising the same rung must collide on one
     * row) AND is amended by ADR-0054 to key on the rung NAME, never the numeric threshold —
     * pin it so a later slider change can't silently re-key it.
     */
    @Test
    fun `notification id is slot-named and stable`() {
        assertThat(CheckBudgetAlertsUseCase.notificationId("abc", "2026-06", BudgetAlertSlot.WARN))
            .isEqualTo("budget:abc:2026-06:warn")
        assertThat(CheckBudgetAlertsUseCase.notificationId("abc", "2026-06", BudgetAlertSlot.LIMIT))
            .isEqualTo("budget:abc:2026-06:limit")
        assertThat(CheckBudgetAlertsUseCase.notificationId("abc", "2026-06", BudgetAlertSlot.OVER))
            .isEqualTo("budget:abc:2026-06:over")
    }

    @Test
    fun `every raised id carries the category prefix the inbox query filters on`() {
        val budget = budget("b1", amount = "10000.00", yearMonth = month)
        val txns = listOf(expense("t1", "10000.00"))
        val result = useCase(listOf(budget), txns, emptySet(), month, zone)
        assertThat(result.map { it.notificationId })
            .containsExactly("budget:b1:2026-06:warn", "budget:b1:2026-06:limit")
    }
}
