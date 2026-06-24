package com.iponlove.app.feature.couple.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `couples` row for Supabase. No `pendingSync` — local-only (ADR-0002).
 * Pushed never (RPC-only writes); pulled like any other synced table.
 */
@Serializable
data class CoupleDto(
    val id: String,
    @SerialName("couple_name") val coupleName: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("user1_id") val user1Id: String,
    @SerialName("user2_id") val user2Id: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
