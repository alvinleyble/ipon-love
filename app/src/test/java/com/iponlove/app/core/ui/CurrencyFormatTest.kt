package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

/** The masked-vs-plain branch behind [money] (Item 15, global Privacy mode). */
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
}
