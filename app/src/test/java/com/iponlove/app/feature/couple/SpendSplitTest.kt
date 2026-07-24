package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.domain.usecase.spendSplit
import org.junit.Test
import java.math.BigDecimal

/**
 * Tier-1 tests for the pure spend-split math (v1.7.0 Item 9 Slice B) that drives the Combined-view
 * banner's amounts row + two-color split bar. Covers distinct amounts, an even split, one-sided
 * spend, and the both-zero divide-by-zero guard (→ 50/50).
 */
class SpendSplitTest {

    @Test
    fun `distinct amounts split by fraction and percent`() {
        val split = spendSplit(mine = BigDecimal("300"), partner = BigDecimal("100"))

        assertThat(split.meFraction).isWithin(0.001f).of(0.75f)
        assertThat(split.mePercent).isEqualTo(75)
        assertThat(split.partnerPercent).isEqualTo(25)
    }

    @Test
    fun `equal amounts are an even fifty-fifty`() {
        val split = spendSplit(mine = BigDecimal("200"), partner = BigDecimal("200"))

        assertThat(split.meFraction).isWithin(0.001f).of(0.5f)
        assertThat(split.mePercent).isEqualTo(50)
        assertThat(split.partnerPercent).isEqualTo(50)
    }

    @Test
    fun `partner zero means the current user carries all of it`() {
        val split = spendSplit(mine = BigDecimal("480"), partner = BigDecimal.ZERO)

        assertThat(split.meFraction).isWithin(0.001f).of(1f)
        assertThat(split.mePercent).isEqualTo(100)
        assertThat(split.partnerPercent).isEqualTo(0)
    }

    @Test
    fun `both zero guards the divide-by-zero with an even split`() {
        val split = spendSplit(mine = BigDecimal.ZERO, partner = BigDecimal.ZERO)

        assertThat(split.meFraction).isWithin(0.001f).of(0.5f)
        assertThat(split.mePercent).isEqualTo(50)
        assertThat(split.partnerPercent).isEqualTo(50)
    }

    @Test
    fun `percents always complement to one hundred`() {
        val split = spendSplit(mine = BigDecimal("1"), partner = BigDecimal("2"))

        assertThat(split.mePercent + split.partnerPercent).isEqualTo(100)
    }
}
