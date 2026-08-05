package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.MerchantHistoryMatch
import com.iponlove.app.feature.transactions.domain.model.Transaction

/**
 * Infers `Category` and `Account` for a scanned receipt from the user's **own** transaction history
 * (v1.7.3 Item 2 Slice 2, ADR-0062 decision 5) — "learns from you".
 *
 * Pure Kotlin over plain [Transaction]s, the same seam [ReceiptParser] uses: nothing here touches
 * Room, ML Kit or a clock, so the whole rule is JVM-testable against fixed rows.
 *
 * The mechanism is deliberately the cheapest one that works: no new schema, no hardcoded PH chain
 * table (which would know only what's hardcoded and miss every sari-sari store), no LLM. It also
 * **bootstraps itself** — the first scan at a new merchant infers nothing, but writes the cleaned
 * merchant into `Note`, so the second scan there has something to match.
 */
object ReceiptHistoryMatcher {

    /**
     * The best match for [merchant] in [history], or null when nothing matched or the matches
     * carried neither a category nor an account.
     *
     * [history] must already be the user's own active expense rows — this function does no
     * ownership filtering of its own (the query does it, so a partner row can never reach here).
     */
    fun match(merchant: String, history: List<Transaction>): MerchantHistoryMatch? {
        val scanned = tokenize(merchant)
        if (scanned.isEmpty()) return null

        // Token-subset either direction: "SM Supermarket" matches a past "SM Supermarket Megamall"
        // and vice versa, because a receipt header and a hand-typed note rarely agree on how much
        // of the name they carry.
        val matches = history.filter { row ->
            val past = tokenize(row.note.orEmpty())
            past.isNotEmpty() && (past.containsAll(scanned) || scanned.containsAll(past))
        }
        if (matches.isEmpty()) return null

        val categoryId = mostFrequent(matches) { it.categoryId }
        val accountId = mostFrequent(matches) { it.accountId }
        if (categoryId == null && accountId == null) return null

        return MerchantHistoryMatch(
            // The caption says "your last … visit", so it names the most recent match's own note.
            merchant = matches.maxByOrNull { it.date }?.note?.trim()?.ifBlank { null } ?: merchant,
            categoryId = categoryId,
            accountId = accountId,
        )
    }

    /**
     * Most frequently paired wins, ties broken by recency (decision 5). Computed **per field**, not
     * by picking one winning row: someone who always buys groceries at the same shop but pays with
     * whatever card is handy should still get the category, and the majority account.
     */
    private fun <T : Any> mostFrequent(rows: List<Transaction>, select: (Transaction) -> T?): T? =
        rows.mapNotNull { row -> select(row)?.let { it to row.date } }
            .groupBy({ it.first }, { it.second })
            .entries
            .maxWithOrNull(compareBy({ it.value.size }, { it.value.max() }))
            ?.key

    /**
     * Case-folded, punctuation-stripped word set. Three things are dropped deliberately:
     * **pure-digit tokens** (the `#0142` branch codes decision 4 already strips from the note, and
     * whatever survived on an older hand-typed row), **single characters** (OCR noise), and a small
     * set of corporate filler words — so `SM SUPERMARKET INC.` and `SM Supermarket` are the same
     * merchant. Note `7-Eleven` reduces to `{eleven}` on both sides, which still matches.
     */
    private fun tokenize(raw: String): Set<String> =
        raw.lowercase()
            .split(NON_WORD)
            .filterTo(mutableSetOf()) { token ->
                token.length >= 2 && !token.all(Char::isDigit) && token !in FILLER
            }

    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")

    private val FILLER = setOf("inc", "corp", "corporation", "ltd", "the", "branch", "store")
}
