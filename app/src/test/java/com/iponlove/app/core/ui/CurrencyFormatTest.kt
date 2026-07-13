package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import org.junit.Test
import java.math.BigDecimal

/**
 * The pure branches behind [money]: the masked-vs-plain Privacy toggle (Item 15) and the
 * display-currency symbol substitution (Item 18).
 */
class CurrencyFormatTest {

    @Test
    fun maskedOrFormatted_privacyOff_returnsFormattedAmount() {
        assertThat(maskedOrFormatted(BigDecimal("1250"), isPrivacyModeOn = false))
            .isEqualTo(formatPhp(BigDecimal("1250")))
    }

    @Test
    fun maskedOrFormatted_privacyOn_returnsMask_regardlessOfAmount() {
        assertThat(maskedOrFormatted(BigDecimal("1250"), isPrivacyModeOn = true)).isEqualTo("•••••")
        assertThat(maskedOrFormatted(BigDecimal.ZERO, isPrivacyModeOn = true)).isEqualTo("•••••")
        assertThat(maskedOrFormatted(BigDecimal("-500"), isPrivacyModeOn = true)).isEqualTo("•••••")
    }

    // --- Item 18: display-currency symbol -------------------------------------------------

    @Test
    fun formatAmount_prependsChosenSymbolGlyph() {
        assertThat(formatAmount(BigDecimal("1250"), CurrencySymbol.PHP)).isEqualTo("₱1,250.00")
        assertThat(formatAmount(BigDecimal("1250"), CurrencySymbol.USD)).isEqualTo("$1,250.00")
        assertThat(formatAmount(BigDecimal("1250"), CurrencySymbol.EUR)).isEqualTo("€1,250.00")
        assertThat(formatAmount(BigDecimal("1250"), CurrencySymbol.JPY)).isEqualTo("¥1,250.00")
    }

    @Test
    fun formatAmount_numberFormattingIsSymbolIndependent() {
        // Only the glyph changes — grouping + 2 decimals (and the pre-existing negative shape) hold.
        assertThat(formatAmount(BigDecimal("0"), CurrencySymbol.KRW)).isEqualTo("₩0.00")
        assertThat(formatAmount(BigDecimal("-1234.5"), CurrencySymbol.GBP)).isEqualTo("£-1,234.50")
    }

    @Test
    fun maskedOrFormatted_privacyOff_usesChosenSymbol() {
        assertThat(maskedOrFormatted(BigDecimal("1250"), isPrivacyModeOn = false, symbol = CurrencySymbol.USD))
            .isEqualTo("$1,250.00")
    }

    @Test
    fun maskedOrFormatted_privacyOn_masksRegardlessOfSymbol() {
        // The mask short-circuits before the symbol is ever applied.
        assertThat(maskedOrFormatted(BigDecimal("1250"), isPrivacyModeOn = true, symbol = CurrencySymbol.USD))
            .isEqualTo("•••••")
    }

    @Test
    fun maskedOrFormatted_defaultSymbolIsPhp_soItem15CallsAreUnchanged() {
        // The 2-arg call (default symbol) stays byte-identical to formatPhp — dormant for anyone
        // who never changes their symbol, preserving the Item 15 contract.
        assertThat(maskedOrFormatted(BigDecimal("1250"), isPrivacyModeOn = false))
            .isEqualTo(formatPhp(BigDecimal("1250")))
    }
}
