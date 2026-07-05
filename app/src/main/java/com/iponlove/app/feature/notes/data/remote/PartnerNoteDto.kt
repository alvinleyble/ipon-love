package com.iponlove.app.feature.notes.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `partner_notes` view row. `title`/`content` are nullable when the note
 * is unshared or deleted (ADR-0005). `couple_id` is always present (the view is gated on
 * it); `created_at` is absent — `updated_at` is used as a fallback during mapping.
 */
@Serializable
data class PartnerNoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String?,
    val content: String?,
    @SerialName("is_shared") val isShared: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
    @SerialName("is_pinned") val isPinned: Boolean = false,
)
