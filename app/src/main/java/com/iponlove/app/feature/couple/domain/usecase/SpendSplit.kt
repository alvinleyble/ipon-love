package com.iponlove.app.feature.couple.domain.usecase

import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * The couple's monthly-expense split for the Combined-view banner (v1.7.0 Item 9 Slice B):
 * the current user's fraction of the combined spend, plus whole-percent labels that sum to 100.
 * Pure + unit-tested — replaces the inline math the old `PartnerSplitSection` carried.
 */
data class SpendSplit(
    /** 0f..1f — the current user's share of the couple's combined monthly expense. */
    val meFraction: Float,
    /** Whole-percent for the current user's label; [partnerPercent] is its complement. */
    val mePercent: Int,
) {
    val partnerPercent: Int get() = 100 - mePercent
}

/**
 * Splits combined monthly expense between the two members. A zero (or negative) total — before
 * either has spent anything this month — yields an even 50/50 rather than a divide-by-zero.
 */
fun spendSplit(mine: BigDecimal, partner: BigDecimal): SpendSplit {
    val total = mine + partner
    val fraction = if (total.signum() <= 0) 0.5f else mine.toFloat() / total.toFloat()
    return SpendSplit(meFraction = fraction, mePercent = (fraction * 100).roundToInt())
}
