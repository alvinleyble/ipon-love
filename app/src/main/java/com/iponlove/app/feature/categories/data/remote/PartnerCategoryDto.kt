package com.iponlove.app.feature.categories.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import com.iponlove.app.feature.categories.domain.model.CategoryType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `partner_categories` view row. Content columns are nullable because the
 * view nulls them when the row is deleted (ADR-0005). `position`/`created_at` are not in
 * the view; defaults are applied during entity mapping.
 */
@Serializable
data class PartnerCategoryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String?,
    val type: CategoryType?,
    val icon: String?,
    val color: String?,
    @SerialName("is_archived") val isArchived: Boolean?,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)
