package com.iponlove.app.feature.notifications.data

import com.iponlove.app.feature.notifications.data.local.NotificationEntity
import com.iponlove.app.feature.notifications.data.remote.NotificationDto
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun NotificationEntity.toDomain(): AppNotification = AppNotification(
    id = id,
    category = NotificationCategory.fromKey(category),
    title = title,
    body = body,
    deepLink = deepLink,
    createdAt = createdAt,
    isRead = isRead,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun NotificationEntity.toDto(): NotificationDto = NotificationDto(
    id = id,
    userId = userId,
    category = category,
    title = title,
    body = body,
    deepLink = deepLink,
    isRead = isRead,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying the
 * server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun NotificationDto.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    userId = userId,
    category = category,
    title = title,
    body = body,
    deepLink = deepLink,
    isRead = isRead,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
