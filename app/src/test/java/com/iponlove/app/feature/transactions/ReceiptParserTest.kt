package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.RecognizedLine
import com.iponlove.app.feature.transactions.domain.usecase.ReceiptParser
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tier-1 coverage for the receipt parser (v1.7.3 Item 2, ADR-0062 decision 4). Deterministic
 * given fixed recognised text, so the OCR call itself is never mocked — that is precisely why
 * [RecognizedLine] is a plain domain type rather than ML Kit's `Text`.
 */
class ReceiptParserTest {

    private val today = LocalDate.of(2026, 8, 5)

    private fun line(text: String, top: Int = 0, height: Int = 20) =
        RecognizedLine(text = text, top = top, height = height)

    private fun amountOf(vararg texts: String): BigDecimal? =
        ReceiptParser.parse(texts.mapIndexed { i, t -> line(t, top = i * 20) }, today).amount

    // --- Amount: keyword tier ------------------------------------------------------------------

    @Test
    fun `total line wins over the cash and change decoys`() {
        // The case that kills "largest number on the page": CASH tendered exceeds the total.
        val amount = amountOf(
            "TOTAL 750.00",
            "CASH 1,000.00",
            "CHANGE 250.00",
        )
        assertThat(amount).isEqualTo(BigDecimal("750.00"))
    }

    @Test
    fun `subtotal is not mistaken for the total`() {
        assertThat(amountOf("SUBTOTAL 669.64", "TOTAL 750.00"))
            .isEqualTo(BigDecimal("750.00"))
    }

    @Test
    fun `vatable and vat breakdown lines are never the amount`() {
        assertThat(amountOf("VATable Sales 669.64", "VAT 12% 80.36", "TOTAL 750.00"))
            .isEqualTo(BigDecimal("750.00"))
    }

    @Test
    fun `amount due outranks a bare total on the same receipt`() {
        // Gap 4: what actually left the wallet wins when a discount makes the two diverge.
        val amount = amountOf(
            "TOTAL 750.00",
            "SC/PWD DISCOUNT 150.00",
            "AMOUNT DUE 600.00",
        )
        assertThat(amount).isEqualTo(BigDecimal("600.00"))
    }

    @Test
    fun `a total line mentioning vat parenthetically is still the total`() {
        // Exclusions are anchored to the start of the line precisely so this survives.
        assertThat(amountOf("TOTAL (VAT INCLUSIVE) 750.00"))
            .isEqualTo(BigDecimal("750.00"))
    }

    @Test
    fun `equal-rank total lines break to the later one`() {
        assertThat(amountOf("TOTAL 100.00", "TOTAL 250.00"))
            .isEqualTo(BigDecimal("250.00"))
    }

    @Test
    fun `cash tendered is rejected even though it carries a number`() {
        assertThat(amountOf("CASH TENDERED 1,000.00", "TOTAL 750.00"))
            .isEqualTo(BigDecimal("750.00"))
    }

    @Test
    fun `thousands separators are parsed`() {
        assertThat(amountOf("TOTAL 1,234.56")).isEqualTo(BigDecimal("1234.56"))
    }

    @Test
    fun `a total printed without decimals is still read`() {
        assertThat(amountOf("TOTAL 750")).isEqualTo(BigDecimal("750"))
    }

    // --- Amount: fallback and blank tiers -------------------------------------------------------

    @Test
    fun `no total keyword falls back to the last money-shaped number`() {
        // The sari-sari case: no TOTAL line at all.
        assertThat(amountOf("Tindahan ni Aling Nena", "Softdrinks 25.00", "Bigas 120.00"))
            .isEqualTo(BigDecimal("120.00"))
    }

    @Test
    fun `the fallback still refuses a change line`() {
        assertThat(amountOf("Item 120.00", "CHANGE 80.00")).isEqualTo(BigDecimal("120.00"))
    }

    @Test
    fun `the fallback ignores digits that are not money-shaped`() {
        // A TIN or a receipt number must never read as an amount — hence the strict two-decimal
        // shape on the blind tier.
        assertThat(amountOf("TIN 123-456-789-000", "OR NO 0142")).isNull()
    }

    @Test
    fun `a percentage is never read as money`() {
        // Money-shaped but immediately followed by '%' — a rate, not an amount.
        assertThat(amountOf("LESS DISCOUNT 12.00%")).isNull()
    }

    @Test
    fun `nothing readable yields no amount rather than a guess`() {
        assertThat(amountOf("THANK YOU", "PLEASE COME AGAIN")).isNull()
    }

    @Test
    fun `empty input parses to an empty result`() {
        val result = ReceiptParser.parse(emptyList(), today)
        assertThat(result.isEmpty).isTrue()
    }

    // --- Date -----------------------------------------------------------------------------------

    private fun dateOf(text: String) = ReceiptParser.parse(listOf(line(text)), today)

    @Test
    fun `a date is resolved month-first per PH convention`() {
        val result = dateOf("07/08/2026")
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 7, 8))
    }

    @Test
    fun `an ambiguous date is flagged as guessed`() {
        // Both parts <= 12, so dd/MM was equally possible — decision 4 marks it like an
        // inferred field rather than presenting it as read.
        assertThat(dateOf("07/08/2026").dateIsAmbiguous).isTrue()
    }

    @Test
    fun `a day above twelve is unambiguous and not flagged`() {
        val result = dateOf("12/25/2025")
        assertThat(result.date).isEqualTo(LocalDate.of(2025, 12, 25))
        assertThat(result.dateIsAmbiguous).isFalse()
    }

    @Test
    fun `a day-first date impossible to read month-first is salvaged`() {
        // 25 cannot be a month, so this reading carries no ambiguity.
        val result = dateOf("25/12/2025")
        assertThat(result.date).isEqualTo(LocalDate.of(2025, 12, 25))
        assertThat(result.dateIsAmbiguous).isFalse()
    }

    @Test
    fun `a two-digit year is expanded`() {
        assertThat(dateOf("07/08/26").date).isEqualTo(LocalDate.of(2026, 7, 8))
    }

    @Test
    fun `a future date is dropped so the form keeps today`() {
        assertThat(dateOf("09/01/2026").date).isNull()
    }

    @Test
    fun `a date older than eighteen months is dropped`() {
        assertThat(dateOf("01/15/2024").date).isNull()
    }

    @Test
    fun `a date exactly inside the eighteen-month bound is kept`() {
        assertThat(dateOf("02/06/2025").date).isEqualTo(LocalDate.of(2025, 2, 6))
    }

    @Test
    fun `an impossible date is not accepted`() {
        assertThat(dateOf("13/45/2026").date).isNull()
    }

    // --- Merchant --------------------------------------------------------------------------------

    private fun merchantOf(vararg lines: RecognizedLine) =
        ReceiptParser.parse(lines.toList(), today).merchant

    @Test
    fun `the largest text near the top becomes the merchant`() {
        val merchant = merchantOf(
            line("SM SUPERMARKET", top = 0, height = 40),
            line("Makati City", top = 50, height = 18),
            line("TOTAL 750.00", top = 400, height = 20),
        )
        // Short all-caps initialisms survive title-casing — "Sm Supermarket" would be wrong.
        assertThat(merchant).isEqualTo("SM Supermarket")
    }

    @Test
    fun `a trailing branch code is stripped`() {
        val merchant = merchantOf(
            line("JOLLIBEE #0142", top = 0, height = 40),
            line("TOTAL 250.00", top = 300, height = 20),
        )
        assertThat(merchant).isEqualTo("Jollibee")
    }

    @Test
    fun `a trailing branch word is stripped`() {
        val merchant = merchantOf(
            line("MERCURY DRUG Makati Branch", top = 0, height = 40),
            line("TOTAL 250.00", top = 300, height = 20),
        )
        assertThat(merchant).isEqualTo("Mercury Drug Makati")
    }

    @Test
    fun `hyphenated names capitalise after the separator`() {
        val merchant = merchantOf(
            line("7-ELEVEN", top = 0, height = 40),
            line("TOTAL 85.00", top = 300, height = 20),
        )
        assertThat(merchant).isEqualTo("7-Eleven")
    }

    @Test
    fun `a big total at the foot never wins the merchant slot`() {
        val merchant = merchantOf(
            line("Aling Nena Store", top = 0, height = 22),
            line("TOTAL 750.00", top = 400, height = 60),
        )
        assertThat(merchant).isEqualTo("Aling Nena")
    }

    @Test
    fun `a top line with no word characters is skipped`() {
        val merchant = merchantOf(
            line("******************", top = 0, height = 40),
            line("PUREGOLD", top = 20, height = 36),
            line("TOTAL 750.00", top = 400, height = 20),
        )
        assertThat(merchant).isEqualTo("Puregold")
    }
}
