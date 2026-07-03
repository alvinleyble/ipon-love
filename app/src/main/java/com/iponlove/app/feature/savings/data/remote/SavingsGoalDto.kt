package com.iponlove.app.feature.savings.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.core.network.serializers.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Wire shape of a `savings_goals` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class SavingsGoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("is_shared") val isShared: Boolean,
    val name: String,
    @SerialName("target_amount")
    @Serializable(with = BigDecimalSerializer::class) val targetAmount: BigDecimal,
    @SerialName("target_date")
    @Serializable(with = LocalDateSerializer::class) val targetDate: LocalDate?,
    val icon: String?,
    val color: String?,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
