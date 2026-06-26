package com.iponlove.app.feature.partnerdebt.data.remote

import com.iponlove.app.core.network.serializers.BigDecimalSerializer
import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/** Wire shape of a `partner_debt_payments` row. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class DebtPaymentDto(
    val id: String,
    @SerialName("debt_id") val debtId: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val note: String?,
    @Serializable(with = InstantSerializer::class) val date: Instant,
    @SerialName("is_netting") val isNetting: Boolean = false,
    @SerialName("counter_debt_id") val counterDebtId: String? = null,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
