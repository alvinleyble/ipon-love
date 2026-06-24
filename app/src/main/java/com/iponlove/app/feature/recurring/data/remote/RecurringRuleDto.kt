package com.iponlove.app.feature.recurring.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.core.network.serializers.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Wire shape of a `recurring_rules` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class RecurringRuleDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val frequency: String,
    val interval: Int,
    @SerialName("next_date")
    @Serializable(with = LocalDateSerializer::class) val nextDate: LocalDate,
    @SerialName("end_date")
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate?,
    val template: RecurringTemplateDto,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)

/** Maps to the rule's `template` jsonb (amount, account_id, category_id, note). */
@Serializable
data class RecurringTemplateDto(
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("account_id") val accountId: String,
    @SerialName("category_id") val categoryId: String,
    val note: String?,
)
