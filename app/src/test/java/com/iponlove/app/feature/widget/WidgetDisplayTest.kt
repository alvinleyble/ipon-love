package com.iponlove.app.feature.widget

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.widget.presentation.WidgetDisplay
import com.iponlove.app.feature.widget.presentation.resolveWidgetDisplay
import org.junit.Test
import java.math.BigDecimal

/**
 * The balance widget's display truth table (grill 2026-07-14). The lock-bypass rows are the point:
 * no session or a set-PIN lock must mask hard and disable the eye, regardless of the local reveal.
 */
class WidgetDisplayTest {

    private val amount = BigDecimal("42350")

    private fun resolve(
        hasSession: Boolean = true,
        isPinSet: Boolean = false,
        isLocked: Boolean = false,
        globalHide: Boolean = false,
        userToggled: Boolean = false,
    ) = resolveWidgetDisplay(
        netAssets = amount,
        symbol = CurrencySymbol.PHP,
        hasSession = hasSession,
        isPinSet = isPinSet,
        isLocked = isLocked,
        globalHide = globalHide,
        userToggled = userToggled,
    )

    // --- Hard mask (fail-closed, eye inert) ---

    @Test fun `no session masks hard even when reveal is toggled on`() {
        assertThat(resolve(hasSession = false, userToggled = true))
            .isEqualTo(WidgetDisplay.HardMasked)
    }

    @Test fun `set-PIN lock masks hard even when reveal is toggled on`() {
        assertThat(resolve(isPinSet = true, isLocked = true, userToggled = true))
            .isEqualTo(WidgetDisplay.HardMasked)
    }

    @Test fun `locked but no PIN set does not hard-mask`() {
        // A user with no app lock is "locked" only by AppLockManager's default flag — never mask them.
        assertThat(resolve(isPinSet = false, isLocked = true))
            .isInstanceOf(WidgetDisplay.Soft::class.java)
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
