package com.iponlove.app.feature.settings.presentation

import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/**
 * Finance sub-screen (v1.6.5 Item 34, split out of Personalize): currency symbol (Item 18),
 * "Hide amounts" privacy toggle (Item 15), and the budget-cycle start day (Item 10b). All three
 * persist instantly on change — no Save/Apply gate (unlike Appearance's themed draft).
 */
data class FinanceUiState(
    /** The user's chosen display-currency symbol (Item 18). Cosmetic glyph swap; no FX. */
    val currencySymbol: CurrencySymbol = CurrencySymbol.DEFAULT,
    /** Global Privacy mode (Item 15) — masks money app-wide when on. */
    val privacyModeEnabled: Boolean = false,
    /** The personal "budget month starts on day N" setting (Item 10b / ADR-0046). 1 = calendar
     *  months (default). Personal budgets only. */
    val budgetStartDay: Int = 1,
)
