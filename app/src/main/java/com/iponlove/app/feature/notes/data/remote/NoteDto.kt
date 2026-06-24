package com.iponlove.app.feature.notes.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire shape of a `notes` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class NoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String?,
    val content: String?,
    @SerialName("is_shared") val isShared: Boolean,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
