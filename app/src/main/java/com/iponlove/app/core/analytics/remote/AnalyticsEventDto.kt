package com.iponlove.app.core.analytics.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** Wire shape of one `analytics_events` row (G10). Push-only — never decoded from a pull. */
@Serializable
data class AnalyticsEventDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val source: String? = null,
    val params: JsonObject? = null,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)
