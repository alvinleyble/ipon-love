package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.presentation.components.parseConfirmAmount
import org.junit.Test

/** The "To confirm" card's per-occurrence amount parse (Item 37). */
class ParseConfirmAmountTest {

    @Test fun plainNumber_parses() {
        assertThat(parseConfirmAmount("19450")!!.toPlainString()).isEqualTo("19450")
    }

    @Test fun decimals_parse() {
        assertThat(parseConfirmAmount("20000.50")!!.toPlainString()).isEqualTo("20000.50")
    }

    @Test fun groupingSeparators_areStripped() {
        // A PH user typing thousands-commas (or a stray space) must not silently post the default.
        assertThat(parseConfirmAmount("19,450")!!.toPlainString()).isEqualTo("19450")
        assertThat(parseConfirmAmount("1 250")!!.toPlainString()).isEqualTo("1250")
    }

    @Test fun blank_isNull_soConfirmUsesTheRuleAmount() {
        assertThat(parseConfirmAmount("")).isNull()
        assertThat(parseConfirmAmount("   ")).isNull()
    }

    @Test fun nonPositive_isNull() {
        assertThat(parseConfirmAmount("0")).isNull()
        assertThat(parseConfirmAmount("-5")).isNull()
    }

    @Test fun garbage_isNull() {
        assertThat(parseConfirmAmount("abc")).isNull()
    }
}
