package com.iponlove.app.feature.savings.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of a `goal_contributions` row. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class GoalContributionDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("user_id") val userId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val note: String?,
    @Serializable(with = InstantSerializer::class) val date: Instant,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
