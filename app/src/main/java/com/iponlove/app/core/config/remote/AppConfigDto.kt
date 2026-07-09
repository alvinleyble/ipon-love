package com.iponlove.app.core.config.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** Wire shape of the single `app_config` row (D3 / ADR-0044). Read-only — never pushed. */
@Serializable
data class AppConfigDto(
    @SerialName("enforcement_enabled") val enforcementEnabled: Boolean,
    @SerialName("cap_overrides") val capOverrides: JsonObject? = null,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)
