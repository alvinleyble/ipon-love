package com.iponlove.app.feature.accounts.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.accounts.domain.model.AccountType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of an `accounts` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class AccountDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val type: AccountType,
    @SerialName("opening_balance")
    @Serializable(with = BigDecimalSerializer::class) val openingBalance: BigDecimal,
    val icon: String?,
    val color: String?,
    val position: Int,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
