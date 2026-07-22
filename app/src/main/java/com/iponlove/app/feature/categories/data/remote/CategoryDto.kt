package com.iponlove.app.feature.categories.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.categories.domain.model.CategoryType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire shape of a `categories` row for Supabase. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class CategoryDto(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("couple_id") val coupleId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    val name: String,
    val type: CategoryType,
    val icon: String?,
    val color: String?,
    val position: Int,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("exclude_from_analysis") val excludeFromAnalysis: Boolean = false,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
