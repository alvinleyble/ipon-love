package com.iponlove.app.feature.transactions.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `partner_transaction_images` view row. [storageUrl] is null when the image or
 * its parent transaction is no longer visible to the partner (private or deleted) — the view
 * still returns the row so the redaction propagates as a purge signal (ADR-0005). The view does
 * not expose `created_at`; the mapper defaults to [Instant.EPOCH].
 */
@Serializable
data class PartnerTransactionImageDto(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("storage_url") val storageUrl: String?,
    val position: Int,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
