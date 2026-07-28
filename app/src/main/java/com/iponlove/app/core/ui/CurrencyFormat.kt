package com.iponlove.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Formats money with the user's chosen display symbol (default ₱, [CurrencySymbol.DEFAULT]).
 * The app is single-currency underneath — the symbol is purely cosmetic (Item 18), no FX; grouping
 * + 2 decimals are always shown.
 */
private val amountFormat = DecimalFormat("#,##0.00")

/** Formats [amount] with [symbol]'s glyph, e.g. `₱1,250.00` / `$1,250.00`. */
fun formatAmount(amount: BigDecimal, symbol: CurrencySymbol): String =
    symbol.glyph + amountFormat.format(amount)

/** PHP-symbol convenience for any non-composable use; the composable [money] path is symbol-aware. */
fun formatPhp(amount: BigDecimal): String = formatAmount(amount, CurrencySymbol.PHP)

private const val MASKED_AMOUNT = "•••••"

/** Global Privacy mode (Item 15) — whether on-screen money should render masked. Provided
 *  once app-wide from [com.iponlove.app.MainActivity]; defaults false for previews/tests. */
val LocalPrivacyMode = staticCompositionLocalOf { false }

/** Write-side counterpart to [LocalPrivacyMode] (Item 16) — flips the global Privacy mode flag.
 *  Provided once app-wide from [com.iponlove.app.MainActivity], wired to
 *  `SetPrivacyModeUseCase`, so any header can drop a [PrivacyEyeAction] in without threading the
 *  flag through that feature's own ViewModel. Defaults to a no-op for previews/tests. */
val LocalTogglePrivacyMode = staticCompositionLocalOf<() -> Unit> { {} }

/** The user's chosen display-currency symbol (Item 18) — provided once app-wide from
 *  [com.iponlove.app.MainActivity]; defaults to PHP for previews/tests. */
val LocalCurrencySymbol = staticCompositionLocalOf { CurrencySymbol.DEFAULT }

/** Pure branch behind [money] — masked when Privacy mode is on, else [formatAmount] with [symbol]. */
fun maskedOrFormatted(
    amount: BigDecimal,
    isPrivacyModeOn: Boolean,
    symbol: CurrencySymbol = CurrencySymbol.PHP,
): String = if (isPrivacyModeOn) MASKED_AMOUNT else formatAmount(amount, symbol)

/** Composable money formatter — the privacy- and symbol-aware counterpart to [formatPhp]. Every
 *  on-screen money value should call this instead, so the global toggles mask/relabel the whole app. */
@Composable
fun money(amount: BigDecimal): String =
    maskedOrFormatted(amount, LocalPrivacyMode.current, LocalCurrencySymbol.current)

/** The current display symbol's glyph, for amount **input-field labels** like `"Amount (₱)"` that
 *  aren't formatted money values but should still track the chosen symbol (Item 18). */
@Composable
fun currencyGlyph(): String = LocalCurrencySymbol.current.glyph
