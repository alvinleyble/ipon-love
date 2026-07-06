package com.iponlove.app.feature.transactions.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire shape of a `transaction_images` row for Supabase Postgrest. */
@Serializable
data class TransactionImageDto(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("storage_url") val storageUrl: String,
    val position: Int,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
