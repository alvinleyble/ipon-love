package com.iponlove.app.core.analytics

import com.iponlove.app.core.analytics.local.AnalyticsEventEntity
import com.iponlove.app.core.analytics.remote.AnalyticsEventDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Buffered Room row → wire DTO. The stored [AnalyticsEventEntity.paramsJson] string is parsed back
 * into a [JsonObject] so it lands in the `jsonb` column as a real object, not a quoted string;
 * null/blank params stay null.
 */
fun AnalyticsEventEntity.toDto(): AnalyticsEventDto = AnalyticsEventDto(
    id = id,
    userId = userId,
    name = name,
    source = source,
    params = paramsJson?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it) as JsonObject },
    createdAt = createdAt,
)
