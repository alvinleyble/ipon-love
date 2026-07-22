package com.iponlove.app.feature.widget.presentation

import com.iponlove.app.core.ui.formatAmount
import com.iponlove.app.feature.accounts.domain.model.AccountBalance
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import java.math.BigDecimal

/** What the balance widget should paint, given session / privacy state. Pure — unit-tested. */
sealed interface WidgetDisplay {
    /**
     * No active session: mask hard **and** hide the eye — a signed-out home screen shows nothing.
     * The app lock deliberately does NOT gate the widget (Alvin's on-device call, 2026-07-14):
     * like the quick-add widget, revealing the balance must not require PIN/biometric — the
     * privacy-mode eye is the only mask for a logged-in user.
     */
    data object HardMasked : WidgetDisplay

    /**
     * Session hint says logged-in but the live Supabase user id hasn't resolved yet (Item 10): a
     * cold process the widget host revived to draw itself — MainActivity never ran, so the SDK
     * session is still restoring and the account/net-assets reads would come back empty. Show a
     * neutral "updating" placeholder (no eye, no false ₱0.00); [WidgetSessionHintWriter] repaints
     * with real data the moment the session becomes Authenticated.
     */
    data object NotReady : WidgetDisplay

    /**
     * Logged-in: the eye is shown. [text] is the formatted amount when [revealed],
     * or `null` when soft-hidden (render the mask).
     */
    data class Soft(val text: String?, val revealed: Boolean) : WidgetDisplay
}

/**
 * The balance widget's display truth table (grill 2026-07-14; lock gate dropped on Alvin's
 * on-device call the same day). No session masks hard and disables the eye. Otherwise the eye
 * toggles a *soft* reveal that is the widget's **own domain** (Alvin, Item 10): it is governed
 * solely by [userToggled] — the widget's own persisted eye state — and is deliberately independent
 * of the in-app "Hide amounts" privacy flag, which scopes to in-app surfaces only. The widget
 * starts masked ([userToggled] defaults false), a tap reveals, and the peek re-hides on the next
 * data refresh (which resets [userToggled] via [Widgets.updateAll]) — so it never survives a cold
 * open, keeping the Items 15/25 "hidden on every cold open" guarantee without reading the in-app flag.
 */
fun resolveWidgetDisplay(
    netAssets: BigDecimal,
    symbol: CurrencySymbol,
    hasSession: Boolean,
    sessionReady: Boolean,
    userToggled: Boolean,
): WidgetDisplay {
    if (!hasSession) return WidgetDisplay.HardMasked
    // Hinted logged-in but the live user id isn't resolved yet → don't paint a false zero (Item 10).
    if (!sessionReady) return WidgetDisplay.NotReady
    // The widget's eye is its own domain — reveal follows only the widget's own toggle, never the
    // in-app privacy flag (Item 10 decision). Default (untoggled) is masked; the eye reveals.
    val revealed = userToggled
    return WidgetDisplay.Soft(
        text = if (revealed) formatAmount(netAssets, symbol) else null,
        revealed = revealed,
    )
}

/** One account row on the tall (list) widget form: a color dot, a name, and a masked-or-real amount. */
data class AccountRowDisplay(
    val name: String,
    /** `account.color` hex for the leading dot; null → the widget's neutral fallback. */
    val colorHex: String?,
    /** Formatted balance when revealed; null when soft-masked (render the `•••••` token). */
    val amountText: String?,
)

/**
 * The tall widget's account rows, keyed off the same [WidgetDisplay] as the header so the list can
 * never disagree with it (grill 2026-07-14, Item 33). **Fail-closed:** [WidgetDisplay.HardMasked]
 * (no session) returns `null` — the whole list is suppressed, hiding account names +
 * count, not just amounts. Under [WidgetDisplay.Soft] every row renders (names always visible); each
 * amount is shown when [WidgetDisplay.Soft.revealed] and masked otherwise, moving in lockstep with
 * the header's single eye. An empty (but non-null) result means "logged in, no active accounts".
 */
fun buildAccountRows(
    display: WidgetDisplay,
    accounts: List<AccountBalance>,
    symbol: CurrencySymbol,
): List<AccountRowDisplay>? = when (display) {
    WidgetDisplay.HardMasked -> null
    // Not-ready suppresses the list too — the "Updating…" hint owns the area (Item 10). The
    // accounts read wouldn't be trustworthy yet anyway.
    WidgetDisplay.NotReady -> null
    is WidgetDisplay.Soft -> accounts.map { (account, balance) ->
        AccountRowDisplay(
            name = account.name,
            colorHex = account.color,
            amountText = if (display.revealed) formatAmount(balance, symbol) else null,
        )
    }
}
