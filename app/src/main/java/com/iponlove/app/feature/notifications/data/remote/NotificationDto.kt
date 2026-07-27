package com.iponlove.app.feature.notifications.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire shape of a `notifications` row. Omits `pendingSync` (local-only, ADR-0002). */
@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val category: String,
    val title: String,
    val body: String,
    @SerialName("deep_link") val deepLink: String?,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_rev") val serverRev: Long?,
)
