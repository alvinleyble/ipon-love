package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.ReceiptHistoryMatcher
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Tier-1 coverage for "learns from you" (v1.7.3 Item 2 Slice 2, ADR-0062 decision 5). Pure over
 * fixed rows — no Room, no clock, no OCR — the same seam `ReceiptParserTest` works against.
 */
class ReceiptHistoryMatcherTest {

    private var clock = 0L

    /** Rows are built in call order, so a later `past(...)` is a more recent visit. */
    private fun past(
        note: String?,
        categoryId: String? = "cat-groceries",
        accountId: String = "acc-cash",
    ) = Transaction(
        id = "t${clock}",
        type = TransactionType.EXPENSE,
        amount = BigDecimal("100.00"),
        accountId = accountId,
        categoryId = categoryId,
        note = note,
        date = Instant.ofEpochSecond(++clock),
    )

    // --- Matching ------------------------------------------------------------------------------

    @Test
    fun `an exact merchant match infers both category and account`() {
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(past("SM Supermarket", categoryId = "cat-groceries", accountId = "acc-bpi")),
        )

        assertThat(match).isNotNull()
        assertThat(match!!.categoryId).isEqualTo("cat-groceries")
        assertThat(match.accountId).isEqualTo("acc-bpi")
    }

    @Test
    fun `a longer scanned name still matches a shorter past note`() {
        // Token-subset, scanned ⊇ past: the receipt header carries the branch, the note didn't.
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket Megamall",
            listOf(past("SM Supermarket")),
        )
        assertThat(match?.categoryId).isEqualTo("cat-groceries")
    }

    @Test
    fun `a shorter scanned name still matches a longer past note`() {
        // The other direction: past ⊇ scanned.
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(past("SM Supermarket Megamall")),
        )
        assertThat(match?.categoryId).isEqualTo("cat-groceries")
    }

    @Test
    fun `branch codes and corporate filler do not defeat a match`() {
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket #0142",
            listOf(past("SM Supermarket Inc.")),
        )
        assertThat(match?.categoryId).isEqualTo("cat-groceries")
    }

    @Test
    fun `an unrelated merchant matches nothing`() {
        val match = ReceiptHistoryMatcher.match("Mercury Drug", listOf(past("SM Supermarket")))
        assertThat(match).isNull()
    }

    @Test
    fun `a first visit to a new merchant infers nothing`() {
        // Decision 5's bootstrap property, stated as a test: the feature is at its weakest on a
        // fresh account, and this is what that looks like.
        assertThat(ReceiptHistoryMatcher.match("Jollibee", emptyList())).isNull()
    }

    @Test
    fun `a merchant that reduces to no usable tokens never matches`() {
        // Digits-only and single characters are dropped, so this has nothing left to match on —
        // and must not fall through to matching everything.
        assertThat(ReceiptHistoryMatcher.match("7 11", listOf(past("SM Supermarket")))).isNull()
    }

    @Test
    fun `rows whose note is blank are never matched`() {
        assertThat(ReceiptHistoryMatcher.match("SM Supermarket", listOf(past(null), past("")))).isNull()
    }

    // --- Winner selection ----------------------------------------------------------------------

    @Test
    fun `the most frequent category wins over a more recent one-off`() {
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(
                past("SM Supermarket", categoryId = "cat-groceries"),
                past("SM Supermarket", categoryId = "cat-groceries"),
                past("SM Supermarket", categoryId = "cat-household"), // most recent, but alone
            ),
        )
        assertThat(match?.categoryId).isEqualTo("cat-groceries")
    }

    @Test
    fun `a tie on frequency breaks to the most recent`() {
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(
                past("SM Supermarket", categoryId = "cat-groceries"),
                past("SM Supermarket", categoryId = "cat-household"),
            ),
        )
        assertThat(match?.categoryId).isEqualTo("cat-household")
    }

    @Test
    fun `category and account are chosen independently of each other`() {
        // Groceries every time, but paid with whatever was handy — the category must not be
        // dragged along by the account's winner, or vice versa.
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(
                past("SM Supermarket", categoryId = "cat-groceries", accountId = "acc-bpi"),
                past("SM Supermarket", categoryId = "cat-groceries", accountId = "acc-gcash"),
                past("SM Supermarket", categoryId = "cat-groceries", accountId = "acc-gcash"),
            ),
        )
        assertThat(match?.categoryId).isEqualTo("cat-groceries")
        assertThat(match?.accountId).isEqualTo("acc-gcash")
    }

    @Test
    fun `an account is still inferred when every matching row is uncategorised`() {
        val match = ReceiptHistoryMatcher.match(
            "SM Supermarket",
            listOf(past("SM Supermarket", categoryId = null, accountId = "acc-gcash")),
        )
        assertThat(match?.categoryId).isNull()
        assertThat(match?.accountId).isEqualTo("acc-gcash")
    }

    // --- The caption's subject -----------------------------------------------------------------

    @Test
    fun `the caption names the merchant as the user last wrote it`() {
        val match = ReceiptHistoryMatcher.match(
            "SM SUPERMARKET #0142",
            listOf(past("SM Supermarket Cubao"), past("SM Supermarket Megamall")),
        )
        // The most recent matching row's own note — "your last … visit", in their words.
        assertThat(match?.merchant).isEqualTo("SM Supermarket Megamall")
    }
}
