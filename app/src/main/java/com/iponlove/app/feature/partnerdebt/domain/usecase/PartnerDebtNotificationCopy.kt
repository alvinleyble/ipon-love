package com.iponlove.app.feature.partnerdebt.domain.usecase

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * The user-facing wording for a "partner logged a new debt" alert — locked at the Item 9 grill.
 * Content-free + amount: the free-text reason stays out (lock-screen caution), but the amount is
 * shown since it's the actionable bit.
 *
 * Formats with a plain local [DecimalFormat] rather than [com.iponlove.app.core.ui.formatPhp] —
 * that helper lives in `core/ui` alongside Compose, and the domain layer stays Android-import-free
 * (CLAUDE.md). It always renders the PHP glyph, same as `formatPhp`'s own non-composable default.
 */
object PartnerDebtNotificationCopy {

    private val amountFormat = DecimalFormat("#,##0.00")

    fun title(partnerName: String): String = "$partnerName logged a new debt"

    fun body(amount: BigDecimal): String = "You owe ₱${amountFormat.format(amount)}."
}
