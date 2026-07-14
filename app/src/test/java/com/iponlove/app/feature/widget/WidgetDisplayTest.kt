package com.iponlove.app.feature.widget

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.widget.presentation.WidgetDisplay
import com.iponlove.app.feature.widget.presentation.resolveWidgetDisplay
import org.junit.Test
import java.math.BigDecimal

/**
 * The balance widget's display truth table (grill 2026-07-14). Only a missing session masks hard
 * and disables the eye — the app lock deliberately does not gate the widget (Alvin's on-device
 * call, 2026-07-14: parity with the quick-add widget, which needs no unlock).
 */
class WidgetDisplayTest {

    private val amount = BigDecimal("42350")

    private fun resolve(
        hasSession: Boolean = true,
        globalHide: Boolean = false,
        userToggled: Boolean = false,
    ) = resolveWidgetDisplay(
        netAssets = amount,
        symbol = CurrencySymbol.PHP,
        hasSession = hasSession,
        globalHide = globalHide,
        userToggled = userToggled,
    )

    // --- Hard mask (fail-closed, eye inert) ---

    @Test fun `no session masks hard even when reveal is toggled on`() {
        assertThat(resolve(hasSession = false, userToggled = true))
            .isEqualTo(WidgetDisplay.HardMasked)
    }

    // --- Soft state: default follows global "Hide amounts", eye flips it ---

    @Test fun `hide off shows the amount by default`() {
        assertThat(resolve(globalHide = false))
            .isEqualTo(WidgetDisplay.Soft(text = "₱42,350.00", revealed = true))
    }

    @Test fun `hide on masks by default with the eye available`() {
        assertThat(resolve(globalHide = true))
            .isEqualTo(WidgetDisplay.Soft(text = null, revealed = false))
    }

    @Test fun `hide on plus a peek reveals the amount`() {
        assertThat(resolve(globalHide = true, userToggled = true))
            .isEqualTo(WidgetDisplay.Soft(text = "₱42,350.00", revealed = true))
    }

    @Test fun `hide off plus a toggle blanks the amount`() {
        assertThat(resolve(globalHide = false, userToggled = true))
            .isEqualTo(WidgetDisplay.Soft(text = null, revealed = false))
    }
}
