package com.iponlove.app.feature.budgets.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of a `budgets` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class BudgetDto(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("category_id") val categoryId: String?,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("year_month") val yearMonth: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
    @SerialName("rollover_enabled") val rolloverEnabled: Boolean = false,
)
