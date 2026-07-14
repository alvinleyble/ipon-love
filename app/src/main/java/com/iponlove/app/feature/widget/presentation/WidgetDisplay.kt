package com.iponlove.app.feature.widget.presentation

import com.iponlove.app.core.ui.formatAmount
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import java.math.BigDecimal

/** What the balance widget should paint, given session / lock / privacy state. Pure — unit-tested. */
sealed interface WidgetDisplay {
    /**
     * No active session, or a set-PIN app lock is engaged: mask hard **and** hide the eye —
     * revealing here would be a way to read the balance without passing the lock (grill 2026-07-14).
     */
    data object HardMasked : WidgetDisplay

    /**
     * Logged-in and unlocked: the eye is shown. [text] is the formatted amount when [revealed],
     * or `null` when soft-hidden (render the mask).
     */
    data class Soft(val text: String?, val revealed: Boolean) : WidgetDisplay
}

/**
 * The balance widget's display truth table (grill 2026-07-14). Fail-closed: no session or a set-PIN
 * lock masks hard and disables the eye. Otherwise the eye toggles a *soft* reveal whose default is
 * the global "Hide amounts" pref (Item 15) — so with hiding on the widget starts masked, a tap
 * peeks, and the peek re-hides on the next data refresh (which resets [userToggled]).
 */
fun resolveWidgetDisplay(
    netAssets: BigDecimal,
    symbol: CurrencySymbol,
    hasSession: Boolean,
    isPinSet: Boolean,
    isLocked: Boolean,
    globalHide: Boolean,
    userToggled: Boolean,
): WidgetDisplay {
    val hardMask = !hasSession || (isPinSet && isLocked)
    if (hardMask) return WidgetDisplay.HardMasked
    // revealed = !(globalHide XOR userToggled): default follows global-hide, the eye flips it.
    val revealed = globalHide == userToggled
    return WidgetDisplay.Soft(
        text = if (revealed) formatAmount(netAssets, symbol) else null,
        revealed = revealed,
    )
}
