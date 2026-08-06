package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.presentation.TransactionEditorReducer
import com.iponlove.app.feature.transactions.presentation.TransactionEditorState
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** The `Save as draft` exit's pure half (ADR-0066): what parks, and what comes back. */
class TransactionEditorDraftExitTest {

    private val parkedAt = Instant.parse("2026-08-06T10:00:00Z")

    private fun editor(
        amountText: String = "",
        note: String = "",
        accountId: String? = null,
        categoryId: String? = null,
        toAccountId: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        images: List<TransactionImage> = emptyList(),
        paidForPartner: Boolean = false,
        amountOwedText: String = "",
        transferFeeText: String = "",
    ) = TransactionEditorState(
        id = "txn-1",
        type = type,
        amountText = amountText,
        note = note,
        accountId = accountId,
        categoryId = categoryId,
        toAccountId = toAccountId,
        date = Instant.ofEpochMilli(5_000),
        images = images,
        paidForPartner = paidForPartner,
        amountOwedText = amountOwedText,
        transferFeeText = transferFeeText,
    )

    private fun image(id: String) =
        TransactionImage(id = id, transactionId = "txn-1", localPath = "/f/$id.jpg", url = null, position = 0)

    // ---- hasDraftContent: nothing to park is not worth a queue row ----

    @Test
    fun anUntouchedFormHasNothingToPark() {
        assertThat(TransactionEditorReducer.hasDraftContent(editor())).isFalse()
    }

    @Test
    fun anyFilledFieldMakesTheFormWorthParking() {
        assertThat(TransactionEditorReducer.hasDraftContent(editor(amountText = "12"))).isTrue()
        assertThat(TransactionEditorReducer.hasDraftContent(editor(note = "SM"))).isTrue()
        assertThat(TransactionEditorReducer.hasDraftContent(editor(accountId = "acc-1"))).isTrue()
        assertThat(TransactionEditorReducer.hasDraftContent(editor(categoryId = "cat-1"))).isTrue()
        assertThat(TransactionEditorReducer.hasDraftContent(editor(toAccountId = "acc-2"))).isTrue()
    }

    /** The scan case the whole feature exists for: a photo alone is worth parking. */
    @Test
    fun aScannedReceiptAloneIsWorthParking() {
        assertThat(TransactionEditorReducer.hasDraftContent(editor(images = listOf(image("img-1")))))
            .isTrue()
    }

    // ---- toDraft ----

    /** A draft that could pass validation wouldn't need to be a draft — nothing is required. */
    @Test
    fun toDraft_parksAnIncompleteForm_withoutValidatingIt() {
        val draft = TransactionEditorReducer.toDraft(editor(note = "SM Supermarket"), parkedAt)

        assertThat(draft.id).isEqualTo("txn-1")
        assertThat(draft.amount).isNull()
        assertThat(draft.accountId).isNull()
        assertThat(draft.categoryId).isNull()
        assertThat(draft.note).isEqualTo("SM Supermarket")
        assertThat(draft.parkedAt).isEqualTo(parkedAt)
    }

    @Test
    fun toDraft_carriesTheReceiptsBothWays() {
        val draft = TransactionEditorReducer.toDraft(
            editor(images = listOf(image("img-1"), image("img-2"))),
            parkedAt,
        )

        assertThat(draft.receiptCount).isEqualTo(2)
        assertThat(draft.localImageIds).containsExactly("img-1", "img-2").inOrder()
    }

    /**
     * Each of these spawns linked rows in another feature (ADR-0019, ADR-0031) and none is
     * meaningful until the transaction is real, so a draft round-trips them blank.
     */
    @Test
    fun toDraft_doesNotPersistPartnerDebtOrTransferFeeIntent() {
        val state = editor(
            amountText = "100",
            paidForPartner = true,
            amountOwedText = "50",
            transferFeeText = "15",
        )

        val restored = TransactionEditorReducer.fromDraft(
            TransactionEditorReducer.toDraft(state, parkedAt),
            images = emptyList(),
        )

        assertThat(restored.paidForPartner).isFalse()
        assertThat(restored.amountOwedText).isEmpty()
        assertThat(restored.transferFeeText).isEmpty()
    }

    @Test
    fun toDraft_normalisesFieldsThatDoNotApplyToTheType() {
        val transfer = TransactionEditorReducer.toDraft(
            editor(type = TransactionType.TRANSFER, categoryId = "cat-1", toAccountId = "acc-2"),
            parkedAt,
        )
        assertThat(transfer.categoryId).isNull()
        assertThat(transfer.toAccountId).isEqualTo("acc-2")

        val expense = TransactionEditorReducer.toDraft(
            editor(type = TransactionType.EXPENSE, categoryId = "cat-1", toAccountId = "acc-2"),
            parkedAt,
        )
        assertThat(expense.toAccountId).isNull()
        assertThat(expense.categoryId).isEqualTo("cat-1")
    }

    // ---- fromDraft ----

    /**
     * Settling a draft creates a NEW transaction under the draft's own id — so `isEditing` stays
     * false (that identity is what makes promotion idempotent, decision 5) and `Save as draft`
     * remains available to re-park it.
     */
    @Test
    fun fromDraft_restoresTheFormAsANewTransaction() {
        val parked = TransactionDraft(
            id = "draft-1",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("120.50"),
            categoryId = "cat-1",
            accountId = "acc-1",
            note = "SM Supermarket",
            date = Instant.ofEpochMilli(5_000),
            receiptCount = 1,
            localImageIds = listOf("img-1"),
            parkedAt = parkedAt,
        )

        val state = TransactionEditorReducer.fromDraft(parked, listOf(image("img-1")))

        assertThat(state.id).isEqualTo("draft-1")
        assertThat(state.isEditing).isFalse()
        assertThat(state.amountText).isEqualTo("120.50")
        assertThat(state.categoryId).isEqualTo("cat-1")
        assertThat(state.accountId).isEqualTo("acc-1")
        assertThat(state.note).isEqualTo("SM Supermarket")
        assertThat(state.date).isEqualTo(Instant.ofEpochMilli(5_000))
        assertThat(state.images.map { it.id }).containsExactly("img-1")
    }

    /** An empty draft must hydrate into a usable blank form, not crash on its nulls. */
    @Test
    fun fromDraft_survivesADraftWithNothingInIt() {
        val state = TransactionEditorReducer.fromDraft(
            TransactionDraft(id = "draft-1", parkedAt = parkedAt),
            images = emptyList(),
        )

        assertThat(state.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(state.amountText).isEmpty()
        assertThat(state.accountId).isNull()
        assertThat(state.note).isEmpty()
    }

    /** Round-trip: park a filled form, restore it, and the user sees what they left. */
    @Test
    fun aFilledFormRoundTripsThroughTheParkingArea() {
        val state = editor(
            amountText = "250.00",
            note = "Jollibee",
            accountId = "acc-1",
            categoryId = "cat-1",
        )

        val restored = TransactionEditorReducer.fromDraft(
            TransactionEditorReducer.toDraft(state, parkedAt),
            images = emptyList(),
        )

        assertThat(restored.amountText).isEqualTo("250.00")
        assertThat(restored.note).isEqualTo("Jollibee")
        assertThat(restored.accountId).isEqualTo("acc-1")
        assertThat(restored.categoryId).isEqualTo("cat-1")
        assertThat(restored.date).isEqualTo(state.date)
    }
}
