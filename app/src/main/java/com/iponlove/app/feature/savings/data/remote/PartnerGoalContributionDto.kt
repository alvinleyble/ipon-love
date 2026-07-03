package com.iponlove.app.feature.savings.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of a `partner_goal_contributions` view row. [amount]/[note]/[date] are null when
 * the contribution is deleted or its parent goal is unshared/deleted (ADR-0005) — the row still
 * crosses so the redaction propagates as a purge (null amount ⇒ purge). `created_at` is absent.
 */
@Serializable
data class PartnerGoalContributionDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("user_id") val userId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal?,
    val note: String?,
    @Serializable(with = InstantSerializer::class) val date: Instant?,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
