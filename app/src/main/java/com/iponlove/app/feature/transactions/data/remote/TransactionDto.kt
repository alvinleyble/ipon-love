package com.iponlove.app.feature.transactions.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of a `transactions` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: TransactionType,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("account_id") val accountId: String,
    @SerialName("to_account_id") val toAccountId: String?,
    @SerialName("category_id") val categoryId: String?,
    val note: String?,
    @Serializable(with = InstantSerializer::class) val date: Instant,
    @SerialName("is_private") val isPrivate: Boolean,
    @SerialName("recurring_rule_id") val recurringRuleId: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
