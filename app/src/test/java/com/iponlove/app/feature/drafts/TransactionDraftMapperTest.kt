package com.iponlove.app.feature.drafts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.drafts.data.toDomain
import com.iponlove.app.feature.drafts.data.toDto
import com.iponlove.app.feature.drafts.data.toEntity
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionDraftMapperTest {

    @Test
    fun entityToDomain_carriesTheParkedTimestampAndLocalImages() {
        val domain = draftEntity(
            "d1",
            localImageIds = listOf("img-1", "img-2"),
            receiptCount = 2,
            createdAt = Instant.ofEpochMilli(7_000),
        ).toDomain()

        assertThat(domain.id).isEqualTo("d1")
        assertThat(domain.amount).isEqualTo(BigDecimal("120.50"))
        assertThat(domain.localImageIds).containsExactly("img-1", "img-2").inOrder()
        assertThat(domain.receiptCount).isEqualTo(2)
        assertThat(domain.parkedAt).isEqualTo(Instant.ofEpochMilli(7_000))
    }

    /** A partial form is the normal case, not an edge case: every content field may be absent. */
    @Test
    fun entityToDomain_survivesAnEntirelyEmptyDraft() {
        val domain = draftEntity(
            "d1",
            type = null,
            amount = null,
            categoryId = null,
            accountId = null,
            note = null,
            date = null,
        ).toDomain()

        assertThat(domain.type).isNull()
        assertThat(domain.amount).isNull()
        assertThat(domain.categoryId).isNull()
        assertThat(domain.accountId).isNull()
        assertThat(domain.note).isNull()
        assertThat(domain.date).isNull()
    }

    /**
     * The row syncs; the photos do not (ADR-0066 decision 4). `localImageIds` is local-only and
     * must never reach the wire — only the count does.
     */
    @Test
    fun toDto_dropsLocalImageIds_butKeepsTheReceiptCount() {
        val dto = draftEntity("d1", localImageIds = listOf("img-1"), receiptCount = 1).toDto()

        assertThat(dto.receiptCount).isEqualTo(1)
        // Structural proof rather than a comment: there is no field to leak into.
        assertThat(dto::class.java.declaredFields.map { it.name }).doesNotContain("localImageIds")
    }

    @Test
    fun dtoToEntity_isServerCanonical_andHoldsNoLocalImages() {
        val entity = draftDto("d1", serverRev = 42, receiptCount = 1).toEntity()

        assertThat(entity.serverRev).isEqualTo(42)
        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.receiptCount).isEqualTo(1)
        // A draft pulled from another device holds no files on THIS one, so it contributes
        // nothing to the orphaned-receipt sweep's known ids (decision 6).
        assertThat(entity.localImageIds).isEmpty()
    }

    @Test
    fun entityRoundTripsThroughTheWire() {
        val original = draftEntity("d1", type = TransactionType.TRANSFER, toAccountId = "acc-2")

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(localImageIds = emptyList()))
    }
}
