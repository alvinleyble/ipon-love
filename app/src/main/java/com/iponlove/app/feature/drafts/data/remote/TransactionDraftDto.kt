package com.iponlove.app.feature.drafts.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of a `transaction_drafts` row.
 *
 * Omits `pendingSync` (local-only, ADR-0002) **and `localImageIds`** (local-only, ADR-0066
 * decision 1): the draft row syncs, the draft's photos do not cross until promotion. A second
 * device renders `receiptCount` as "📷 1 receipt — on your other device" instead.
 *
 * Every content field is nullable — a draft is a partial form.
 */
@Serializable
data class TransactionDraftDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: TransactionType? = null,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("to_account_id") val toAccountId: String? = null,
    val note: String? = null,
    @Serializable(with = InstantSerializer::class) val date: Instant? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("receipt_count") val receiptCount: Int = 0,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
