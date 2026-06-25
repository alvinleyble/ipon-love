package com.iponlove.app.feature.partnerdebt.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of a `partner_debts` row. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class PartnerDebtDto(
    val id: String,
    @SerialName("couple_id") val coupleId: String,
    @SerialName("borrower_id") val borrowerId: String,
    @SerialName("lender_id") val lenderId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
