package com.iponlove.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Formats money as Philippine pesos, e.g. `₱1,250.00`. V1 is PHP-only, so the symbol
 * is fixed; grouping + 2 decimals are always shown.
 */
private val phpFormat = DecimalFormat("#,##0.00")

fun formatPhp(amount: BigDecimal): String = "₱" + phpFormat.format(amount)

private const val MASKED_AMOUNT = "•••••"

/** Global Privacy mode (Item 15) — whether on-screen money should render masked. Provided
 *  once app-wide from [com.iponlove.app.MainActivity]; defaults false for previews/tests. */
val LocalPrivacyMode = staticCompositionLocalOf { false }

/** Pure branch behind [money] — masked when Privacy mode is on, [formatPhp] otherwise. */
fun maskedOrFormatted(amount: BigDecimal, isPrivacyModeOn: Boolean): String =
    if (isPrivacyModeOn) MASKED_AMOUNT else formatPhp(amount)

/** Composable money formatter — the privacy-aware counterpart to [formatPhp]. Every on-screen
 *  money value should call this instead, so a single global toggle masks the whole app. */
@Composable
fun money(amount: BigDecimal): String = maskedOrFormatted(amount, LocalPrivacyMode.current)
