package com.iponlove.app.core.ui

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Formats money as Philippine pesos, e.g. `₱1,250.00`. V1 is PHP-only, so the symbol
 * is fixed; grouping + 2 decimals are always shown.
 */
private val phpFormat = DecimalFormat("#,##0.00")

fun formatPhp(amount: BigDecimal): String = "₱" + phpFormat.format(amount)
