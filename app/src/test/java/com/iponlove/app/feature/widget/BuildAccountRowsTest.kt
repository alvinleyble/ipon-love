package com.iponlove.app.feature.widget

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountBalance
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.widget.presentation.WidgetDisplay
import com.iponlove.app.feature.widget.presentation.buildAccountRows
import org.junit.Test
import java.math.BigDecimal

/**
 * The tall widget's account rows (Item 33). The list mirrors the header's [WidgetDisplay]: a hard
 * mask suppresses it entirely (fail-closed — names + count hidden, not just amounts), a soft hide
 * keeps names but masks amounts, and a reveal shows everything, all in row order.
 */
class BuildAccountRowsTest {

    private fun bal(name: String, amount: String, color: String? = null) = AccountBalance(
        account = Account(
            id = name,
            name = name,
            type = AccountType.EWALLET,
            openingBalance = BigDecimal.ZERO,
            color = color,
        ),
        balance = BigDecimal(amount),
    )

    private val accounts = listOf(
        bal("Cash", "500.00", color = "#4CAF50"),
        bal("GCash", "1200.50"),
    )

    @Test fun `hard mask suppresses the whole list`() {
        assertThat(buildAccountRows(WidgetDisplay.HardMasked, accounts, CurrencySymbol.PHP)).isNull()
    }

    @Test fun `revealed soft state shows names and real amounts in order`() {
        val rows = buildAccountRows(WidgetDisplay.Soft(text = "x", revealed = true), accounts, CurrencySymbol.PHP)!!
        assertThat(rows.map { it.name }).containsExactly("Cash", "GCash").inOrder()
        assertThat(rows[0].amountText).isEqualTo("₱500.00")
        assertThat(rows[1].amountText).isEqualTo("₱1,200.50")
        assertThat(rows[0].colorHex).isEqualTo("#4CAF50")
        assertThat(rows[1].colorHex).isNull()
    }

    @Test fun `hidden soft state keeps names but masks amounts`() {
        val rows = buildAccountRows(WidgetDisplay.Soft(text = null, revealed = false), accounts, CurrencySymbol.PHP)!!
        assertThat(rows.map { it.name }).containsExactly("Cash", "GCash").inOrder()
        assertThat(rows.map { it.amountText }).containsExactly(null, null)
    }

    @Test fun `logged in with no accounts is empty but not suppressed`() {
        val rows = buildAccountRows(WidgetDisplay.Soft(text = "x", revealed = true), emptyList(), CurrencySymbol.PHP)
        assertThat(rows).isNotNull()
        assertThat(rows!!).isEmpty()
    }

    @Test fun `the chosen currency symbol formats each row`() {
        val rows = buildAccountRows(WidgetDisplay.Soft(text = "x", revealed = true), accounts, CurrencySymbol.USD)!!
        assertThat(rows[0].amountText).isEqualTo("$500.00")
    }
}
