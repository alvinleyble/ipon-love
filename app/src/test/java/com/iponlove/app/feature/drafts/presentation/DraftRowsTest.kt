package com.iponlove.app.feature.drafts.presentation

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.drafts.draft
import org.junit.Test
import java.time.Instant

class DraftRowsTest {

    private val now = Instant.parse("2026-08-06T02:00:00Z")
    private val categories = mapOf("cat-1" to "Groceries")
    private val accounts = mapOf("acc-1" to "GCash")

    private fun rows(
        drafts: List<com.iponlove.app.feature.drafts.domain.model.TransactionDraft>,
        localPathFor: (String) -> String? = { null },
    ) = draftRows(drafts, categories, accounts, now, localPathFor)

    @Test
    fun namesTheDraftAfterItsNote_withCategoryAndAccountBeneath() {
        val row = rows(listOf(draft(note = "SM Supermarket"))).single()

        assertThat(row.title).isEqualTo("SM Supermarket")
        assertThat(row.subtitle).isEqualTo("Groceries · GCash")
    }

    @Test
    fun fallsBackToTheCategory_thenToAPlaceholder() {
        assertThat(rows(listOf(draft(note = null))).single().title).isEqualTo("Groceries")
        assertThat(rows(listOf(draft(note = null, categoryId = null))).single().title)
            .isEqualTo("Untitled draft")
    }

    /**
     * The table carries no FK on `category_id`/`account_id` precisely so a parked draft survives
     * its category being archived while it waits — which makes "gone" a display concern, here.
     */
    @Test
    fun aDeletedCategoryOrAccountDegradesGracefully() {
        val row = rows(listOf(draft(categoryId = "vanished", accountId = "vanished"))).single()

        assertThat(row.subtitle).isEqualTo("No category · No account")
        assertThat(row.title).isEqualTo("SM Supermarket")
    }

    @Test
    fun anAmountlessDraftKeepsANullAmount_ratherThanReadingAsZero() {
        assertThat(rows(listOf(draft(amount = null))).single().amount).isNull()
    }

    @Test
    fun aLocalReceiptBecomesTheRowThumbnail() {
        val row = rows(
            drafts = listOf(draft(localImageIds = listOf("img-1"), receiptCount = 1)),
            localPathFor = { id -> "/files/receipts/$id.jpg".takeIf { id == "img-1" } },
        ).single()

        assertThat(row.thumbnailPath).isEqualTo("/files/receipts/img-1.jpg")
        assertThat(row.receiptsOnOtherDevice).isFalse()
    }

    /**
     * Decision 4's accepted weak point, surfaced honestly: the row syncs but the photo doesn't, so
     * a receipt-bearing draft on a second device has a count and no file.
     */
    @Test
    fun aDraftSyncedFromAnotherDeviceSaysWhereItsPhotoIs() {
        val row = rows(listOf(draft(receiptCount = 1, localImageIds = emptyList()))).single()

        assertThat(row.thumbnailPath).isNull()
        assertThat(row.receiptsOnOtherDevice).isTrue()
    }

    /** No receipts at all is not "on your other device" — it's just a hand-typed draft. */
    @Test
    fun aPhotolessDraftClaimsNoRemoteReceipt() {
        assertThat(rows(listOf(draft())).single().receiptsOnOtherDevice).isFalse()
    }

    /**
     * A draft that holds local ids whose files have since gone (the user removed the photo in the
     * editor and backed out) shows no thumbnail and says nothing — pointing at another device that
     * hasn't got the photo either would be worse than silence.
     */
    @Test
    fun aLocallyOwnedButMissingFileDoesNotClaimAnotherDevice() {
        val row = rows(listOf(draft(localImageIds = listOf("img-1"), receiptCount = 1))).single()

        assertThat(row.thumbnailPath).isNull()
        assertThat(row.receiptsOnOtherDevice).isFalse()
    }

    @Test
    fun carriesTheAgeLabel() {
        val row = rows(listOf(draft(parkedAt = Instant.parse("2026-07-25T02:00:00Z")))).single()

        assertThat(row.ageLabel).isEqualTo("parked 12 days ago")
    }
}
