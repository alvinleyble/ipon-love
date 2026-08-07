package com.iponlove.app.feature.widget

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.widget.presentation.QuickAddForm
import com.iponlove.app.feature.widget.presentation.hasDraftContent
import com.iponlove.app.feature.widget.presentation.toDraft
import com.iponlove.app.feature.widget.presentation.toTransaction
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The Quick Add sheet's pure half (v1.7.3 Item 14, ADR-0067) — what parks, what saves, and the
 * pre-generated id that ties the two together.
 */
class QuickAddFormTest {

    private val id = "quick-1"
    private val parkedAt: Instant = Instant.parse("2026-08-07T10:00:00Z")

    private fun form(
        type: TransactionType = TransactionType.EXPENSE,
        amountText: String = "",
        accountId: String? = null,
        categoryId: String? = null,
        note: String = "",
        image: TransactionImage? = null,
    ) = QuickAddForm(
        id = id,
        type = type,
        amountText = amountText,
        accountId = accountId,
        categoryId = categoryId,
        note = note,
        image = image,
    )

    private fun image(imageId: String = "img-1") = TransactionImage(
        id = imageId,
        transactionId = id,
        localPath = "/f/$imageId.jpg",
        url = null,
        position = 0,
    )

    // ---- hasDraftContent: an untouched sheet must not mint an empty queue row ----

    @Test
    fun anUntouchedSheetHasNothingToPark() {
        assertThat(form().hasDraftContent()).isFalse()
    }

    @Test
    fun eachFieldOnItsOwnIsWorthParking() {
        assertThat(form(amountText = "120").hasDraftContent()).isTrue()
        assertThat(form(note = "SM Supermarket").hasDraftContent()).isTrue()
        assertThat(form(categoryId = "cat-1").hasDraftContent()).isTrue()
        assertThat(form(accountId = "acc-1").hasDraftContent()).isTrue()
        assertThat(form(image = image()).hasDraftContent()).isTrue()
    }

    /**
     * The sheet *displays* the first account as a default. Content is read off the form, not the
     * resolved state, so that default alone must not make an untouched sheet parkable.
     */
    @Test
    fun theDisplayedDefaultAccountIsNotContent() {
        val untouched = form(accountId = null)
        assertThat(untouched.hasDraftContent()).isFalse()
        // Resolving a default for display doesn't touch the form, so the answer can't change.
        assertThat(untouched.toDraft(accountId = "acc-default", parkedAt = parkedAt).accountId)
            .isEqualTo("acc-default")
    }

    // ---- toDraft: nothing is validated, and the pre-generated id carries ----

    @Test
    fun aParkedDraftCarriesThePreGeneratedId() {
        assertThat(form(amountText = "12").toDraft(accountId = "acc-1", parkedAt = parkedAt).id)
            .isEqualTo(id)
    }

    @Test
    fun anAmountLessAccountLessCategoryLessSheetStillParks() {
        val draft = form(note = "jeepney").toDraft(accountId = null, parkedAt = parkedAt)

        assertThat(draft.amount).isNull()
        assertThat(draft.accountId).isNull()
        assertThat(draft.categoryId).isNull()
        assertThat(draft.note).isEqualTo("jeepney")
        assertThat(draft.parkedAt).isEqualTo(parkedAt)
    }

    @Test
    fun aBlankNoteParksAsNullNotAsWhitespace() {
        val draft = form(amountText = "12", note = "   ").toDraft(accountId = "acc-1", parkedAt = parkedAt)
        assertThat(draft.note).isNull()
    }

    @Test
    fun theParkedAmountIsParsedFromTheTypedText() {
        val draft = form(amountText = " 249.50 ").toDraft(accountId = "acc-1", parkedAt = parkedAt)
        assertThat(draft.amount).isEqualTo(BigDecimal("249.50"))
    }

    @Test
    fun anUnparseableAmountParksAsNullRatherThanFailing() {
        val draft = form(amountText = "abc").toDraft(accountId = "acc-1", parkedAt = parkedAt)
        assertThat(draft.amount).isNull()
    }

    @Test
    fun aScannedReceiptParksItsCountAndItsLocalId() {
        val draft = form(image = image("img-7")).toDraft(accountId = "acc-1", parkedAt = parkedAt)

        assertThat(draft.receiptCount).isEqualTo(1)
        assertThat(draft.localImageIds).containsExactly("img-7")
    }

    @Test
    fun aSheetWithNoReceiptParksNoImageIds() {
        val draft = form(amountText = "12").toDraft(accountId = "acc-1", parkedAt = parkedAt)

        assertThat(draft.receiptCount).isEqualTo(0)
        assertThat(draft.localImageIds).isEmpty()
    }

    /** Quick Add has no TRANSFER leg and no private toggle — a draft round-trips both blank. */
    @Test
    fun quickAddNeverParksATransferLegOrAPrivateFlag() {
        val draft = form(amountText = "12").toDraft(accountId = "acc-1", parkedAt = parkedAt)

        assertThat(draft.toAccountId).isNull()
        assertThat(draft.isPrivate).isFalse()
    }

    // ---- toTransaction: the same id, so promotion is an idempotent upsert ----

    @Test
    fun theSavedTransactionCarriesTheSamePreGeneratedIdAsTheDraft() {
        val f = form(amountText = "300", categoryId = "cat-1")
        val draft = f.toDraft(accountId = "acc-1", parkedAt = parkedAt)
        val transaction = f.toTransaction(
            amount = BigDecimal("300"),
            accountId = "acc-1",
            date = parkedAt,
        )

        // The identity ADR-0066 decision 5 rests on: retiring the draft after the write is then an
        // idempotent upsert of the same row, so a failed retire can never double the money.
        assertThat(transaction.id).isEqualTo(draft.id)
        assertThat(transaction.id).isEqualTo(id)
    }

    @Test
    fun aSavedTransactionCarriesTheTypedNote() {
        val transaction = form(note = " Jollibee ").toTransaction(
            amount = BigDecimal("120"),
            accountId = "acc-1",
            date = parkedAt,
        )
        assertThat(transaction.note).isEqualTo("Jollibee")
    }

    /** `note` used to be hardcoded to null on this path — the actual gap Item 14 closes. */
    @Test
    fun anEmptyNoteSavesAsNull() {
        val transaction = form().toTransaction(
            amount = BigDecimal("120"),
            accountId = "acc-1",
            date = parkedAt,
        )
        assertThat(transaction.note).isNull()
    }

    @Test
    fun aSavedTransactionKeepsTypeCategoryAccountAndDate() {
        val transaction = form(type = TransactionType.INCOME, categoryId = "cat-9").toTransaction(
            amount = BigDecimal("1000"),
            accountId = "acc-2",
            date = parkedAt,
        )

        assertThat(transaction.type).isEqualTo(TransactionType.INCOME)
        assertThat(transaction.categoryId).isEqualTo("cat-9")
        assertThat(transaction.accountId).isEqualTo("acc-2")
        assertThat(transaction.date).isEqualTo(parkedAt)
        assertThat(transaction.amount).isEqualTo(BigDecimal("1000"))
        assertThat(transaction.toAccountId).isNull()
        assertThat(transaction.isPrivate).isFalse()
    }
}
