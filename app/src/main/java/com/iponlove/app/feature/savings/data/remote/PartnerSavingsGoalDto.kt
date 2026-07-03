package com.iponlove.app.feature.savings.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.core.network.serializers.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Wire shape of a `partner_savings_goals` view row. Content columns are nullable — the view
 * nulls them when the goal is unshared or deleted (ADR-0005) so the removal still crosses.
 * `couple_id` is always present (the view is gated on it); `created_at` is absent — the mapper
 * falls back to `updated_at`.
 */
@Serializable
data class PartnerSavingsGoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String?,
    @SerialName("target_amount")
    @Serializable(with = BigDecimalSerializer::class) val targetAmount: BigDecimal?,
    @SerialName("target_date")
    @Serializable(with = LocalDateSerializer::class) val targetDate: LocalDate?,
    val icon: String?,
    val color: String?,
    @SerialName("is_archived") val isArchived: Boolean?,
    @SerialName("is_shared") val isShared: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
