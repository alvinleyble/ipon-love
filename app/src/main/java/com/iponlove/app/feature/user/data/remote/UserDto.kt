package com.iponlove.app.feature.user.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `users` row for Supabase. No `isDeleted` — users are never soft-deleted
 * (the column doesn't exist in the schema). No `pendingSync` — local-only (ADR-0002).
 */
@Serializable
data class UserDto(
    val id: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("accent_color") val accentColor: String?,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
