package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.ReceiptScanResult
import com.iponlove.app.feature.transactions.domain.model.RecognizedLine
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Turns recognised receipt text into a prefilled draft (v1.7.3 Item 2, ADR-0062 decision 4).
 *
 * Pure Kotlin and deterministic given fixed input — the whole reason
 * [RecognizedLine] exists rather than passing ML Kit's own `Text` through. No LLM, no merchant
 * table, no network (decision 5's rejected alternatives).
 */
object ReceiptParser {

    fun parse(lines: List<RecognizedLine>, today: LocalDate): ReceiptScanResult {
        if (lines.isEmpty()) return ReceiptScanResult()
        val date = parseDate(lines, today)
        return ReceiptScanResult(
            amount = parseAmount(lines),
            date = date?.value,
            dateIsAmbiguous = date?.ambiguous == true,
            merchant = parseMerchant(lines),
        )
    }

    // --- Amount -------------------------------------------------------------------------------

    /**
     * A three-tier ladder (Item 5's gap 3): a keyword line, else the last money-shaped number in
     * reading order, else blank — **never a guess**. "Largest number on the page" is deliberately
     * not a tier: on a PH receipt the `CASH` tendered routinely exceeds the total.
     *
     * A line is rejected outright when it *starts with* an exclusion keyword (`SUBTOTAL`,
     * `VATABLE`, `VAT`, `CASH`, `TENDERED`, `CHANGE`). Anchoring exclusions to the start rather
     * than anywhere in the line is what lets `TOTAL (VAT INCLUSIVE)` survive while `VAT 12%` does
     * not — receipt lines are `LABEL … amount`, so the label sits at the front.
     *
     * Surviving lines carrying a total-family keyword are ranked (gap 4): what actually left the
     * wallet wins, so `AMOUNT DUE` / `TOTAL DUE` / `NET AMOUNT` / `GRAND TOTAL` beat a bare
     * `TOTAL` — the divergence an SC/PWD discount or a service charge creates. Ties break to the
     * later line, totals being printed at the foot.
     */
    private fun parseAmount(lines: List<RecognizedLine>): BigDecimal? {
        val candidates = lines.mapIndexedNotNull { index, line ->
            val normalized = line.text.normalized()
            if (startsWithExclusion(normalized)) return@mapIndexedNotNull null
            val rank = totalKeywordRank(normalized) ?: return@mapIndexedNotNull null
            val amount = lastMoneyIn(line.text, LOOSE_MONEY) ?: return@mapIndexedNotNull null
            Triple(rank, index, amount)
        }
        candidates.maxWithOrNull(compareBy({ it.first }, { it.second }))?.let { return it.third }

        // Tier 2 — the blind fallback. Demands the strict two-decimal money shape so a TIN, a
        // phone number or a branch code can't pass as an amount; tier 1 can afford a looser
        // number because it already knows the line is a total.
        return lines.asReversed()
            .firstNotNullOfOrNull { line ->
                if (startsWithExclusion(line.text.normalized())) null
                else lastMoneyIn(line.text, STRICT_MONEY)
            }
    }

    private fun startsWithExclusion(normalized: String): Boolean =
        AMOUNT_EXCLUSIONS.any { normalized.startsWith(it) && normalized.endsWordAt(it.length) }

    private fun totalKeywordRank(normalized: String): Int? = when {
        isCountLine(normalized) -> null
        STRONG_TOTAL_KEYWORDS.any { normalized.containsWord(it) } -> 2
        normalized.containsWord("total") -> 1
        else -> null
    }

    /** `TOTAL ITEMS 3` / `TOTAL QTY 12` counts a basket, not money — and being printed *below* the
     *  real total, the rank-1 tie-break would otherwise prefer it and prefill `3`. */
    private fun isCountLine(normalized: String): Boolean =
        COUNT_MARKERS.any { normalized.containsWord(it) } ||
            normalized.contains(NUMBER_OF_PREFIX)

    /** The last money-shaped number on a line — the amount trails its label. A match immediately
     *  followed by `%` is skipped so a tax rate never reads as money. */
    private fun lastMoneyIn(text: String, pattern: Regex): BigDecimal? =
        pattern.findAll(text)
            .filterNot { text.getOrNull(it.range.last + 1) == '%' }
            .lastOrNull()
            ?.value
            ?.replace(",", "")
            ?.toBigDecimalOrNull()

    // --- Date ---------------------------------------------------------------------------------

    private data class ParsedDate(val value: LocalDate, val ambiguous: Boolean)

    /**
     * Numeric dates only (`MM/dd/yyyy`, `M-d-yy`, …), resolved **month-first** per PH convention,
     * then sanity-bounded: never in the future, never more than 18 months old. A result failing
     * the bound is dropped rather than forced — the form's own default (today) is the fallback,
     * so the parser never has to reach for a clock it doesn't own.
     *
     * `07/08/2026` is genuinely ambiguous; when both leading parts are ≤ 12 the month-first
     * reading is flagged so the UI can mark it as guessed rather than read (decision 4).
     */
    private fun parseDate(lines: List<RecognizedLine>, today: LocalDate): ParsedDate? {
        val earliestAcceptable = today.minusMonths(18)
        return lines.firstNotNullOfOrNull { line ->
            DATE_PATTERN.findAll(line.text).firstNotNullOfOrNull { match ->
                val (first, second, rawYear) = match.destructured
                val year = expandYear(rawYear.toInt())
                // Month-first by default. A leading part above 12 can only be day-first, so that
                // one reading is salvaged rather than dropped — and it is unambiguous by
                // construction, hence never flagged.
                val monthFirst = dateOrNull(year, first.toInt(), second.toInt())
                val parsed = monthFirst?.let { ParsedDate(it, ambiguous = second.toInt() <= 12) }
                    ?: dateOrNull(year, second.toInt(), first.toInt())
                        ?.let { ParsedDate(it, ambiguous = false) }
                parsed?.takeIf { it.value <= today && it.value >= earliestAcceptable }
            }
        }
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    private fun expandYear(raw: Int): Int = if (raw < 100) 2000 + raw else raw

    // --- Merchant -----------------------------------------------------------------------------

    /**
     * The largest text near the top (decision 4), written to `Note` cleaned — which is what makes
     * it self-reinforcing input for Slice 2's history inference (decision 5). "Near the top" is
     * the upper third of the recognised content's vertical span; "largest" is the tallest bounding
     * box within it, ties going to the earlier line.
     */
    private fun parseMerchant(lines: List<RecognizedLine>): String? {
        val minTop = lines.minOf { it.top }
        val maxTop = lines.maxOf { it.top }
        val threshold = minTop + (maxTop - minTop) / 3
        return lines.asSequence()
            .filter { it.top <= threshold }
            .sortedWith(compareByDescending<RecognizedLine> { it.height }.thenBy { it.top })
            .mapNotNull { cleanMerchant(it.text) }
            .firstOrNull()
    }

    /** Strips trailing branch/store codes (`#0142`, `BRANCH 5`, `STORE #3`) and title-cases the
     *  rest, preserving short all-caps initialisms so `SM SUPERMARKET` reads `SM Supermarket`
     *  rather than `Sm Supermarket`. Returns null when nothing word-like survives. */
    private fun cleanMerchant(raw: String): String? {
        val stripped = raw.trim()
            .replace(BRANCH_SUFFIX, "")
            .trim()
            .trim('-', '–', ',', ':', '.', '*', '=', '|')
            .trim()
        if (stripped.count { it.isLetter() } < 2) return null
        return stripped.split(WHITESPACE).joinToString(" ") { titleCase(it) }
    }

    private fun titleCase(token: String): String {
        if (token.length <= 3 && token.all { it.isLetter() } && token.all { it.isUpperCase() }) {
            return token
        }
        val lowered = token.lowercase()
        return buildString(lowered.length) {
            lowered.forEachIndexed { index, char ->
                val previous = lowered.getOrNull(index - 1)
                val startsWord = previous == null || (!previous.isLetter() && previous != '\'')
                append(if (startsWord) char.uppercaseChar() else char)
            }
        }
    }

    // --- Shared -------------------------------------------------------------------------------

    private fun String.normalized(): String = lowercase().replace(WHITESPACE, " ").trim()

    private fun String.containsWord(word: String): Boolean {
        var from = 0
        while (true) {
            val at = indexOf(word, from)
            if (at < 0) return false
            val boundedBefore = at == 0 || !this[at - 1].isLetterOrDigit()
            if (boundedBefore && endsWordAt(at + word.length)) return true
            from = at + 1
        }
    }

    private fun String.endsWordAt(index: Int): Boolean =
        index >= length || !this[index].isLetterOrDigit()

    private val WHITESPACE = Regex("""\s+""")

    /** Ranked above a bare `TOTAL`: these name what was actually paid after discounts/charges. */
    private val STRONG_TOTAL_KEYWORDS =
        listOf("amount due", "total due", "net amount", "amount payable", "grand total")

    /** Words that make a `TOTAL …` line a basket count rather than money — see [isCountLine]. */
    private val COUNT_MARKERS =
        listOf("item", "items", "qty", "quantity", "pc", "pcs", "piece", "pieces", "count")

    /** `NO. OF ITEMS` / `NO OF ITEMS` — the one count phrasing that isn't a single word. */
    private val NUMBER_OF_PREFIX = Regex("""\bno\.?\s+of\b""")

    /** Checked against the *start* of the line only — see [parseAmount]. */
    private val AMOUNT_EXCLUSIONS =
        listOf("subtotal", "sub total", "vatable", "vat", "cash", "tendered", "change")

    /** Trailing branch/store codes — `#0142`, `Branch`, `Store #3` — dropped so the same shop
     *  normalises to the same `Note` across visits, which is what Slice 2's matcher keys on. */
    private val BRANCH_SUFFIX = Regex(
        """\s*(?:#\s*\d+|\b(?:branch|br|store)\b\s*#?\s*\d*)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val LOOSE_MONEY = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""")
    private val STRICT_MONEY = Regex("""\d{1,3}(?:,\d{3})+\.\d{2}|\d+\.\d{2}""")
    private val DATE_PATTERN = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2}|\d{4})\b""")
}
