package com.iponlove.app.feature.transactions.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of a `partner_transactions` view row. Content columns (type, amount, ids, note,
 * date) are nullable because the view nulls them when the row is private or deleted (ADR-0005).
 * `created_at`/`recurring_rule_id` are not in the view; defaults are applied during mapping.
 */
@Serializable
data class PartnerTransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: TransactionType?,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal?,
    @SerialName("account_id") val accountId: String?,
    @SerialName("to_account_id") val toAccountId: String?,
    @SerialName("category_id") val categoryId: String?,
    val note: String?,
    @Serializable(with = InstantSerializer::class) val date: Instant?,
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("is_settlement") val isSettlement: Boolean = false,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
