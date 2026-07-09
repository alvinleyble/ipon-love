package com.iponlove.app.feature.user.data

import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.entitlement.EntitlementSource
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.domain.model.User

/**
 * The 4 entitlement columns as a [core/entitlement][Entitlement] snapshot (D2 / ADR-0044). An
 * unrecognized [entitlementSource] string (a future value this build doesn't know) fails open to
 * [EntitlementSource.NONE] rather than crashing — a bad remote value must never break a read.
 */
fun UserEntity.toEntitlement(): Entitlement = Entitlement(
    isPremium = isPremium,
    premiumUntil = premiumUntil,
    source = runCatching { EntitlementSource.valueOf(entitlementSource) }
        .getOrDefault(EntitlementSource.NONE),
)

fun UserEntity.toDomain(): User = User(
    id = id,
    displayName = displayName,
    accentColor = accentColor,
    coupleId = coupleId,
    createdAt = createdAt,
)

fun UserEntity.toDto(): UserDto = UserDto(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    accentColor = accentColor,
    coupleId = coupleId,
    isPremium = isPremium,
    premiumUntil = premiumUntil,
    entitlementSource = entitlementSource,
    entitlementCheckedAt = entitlementCheckedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    serverRev = serverRev,
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    accentColor = accentColor,
    coupleId = coupleId,
    isPremium = isPremium,
    premiumUntil = premiumUntil,
    entitlementSource = entitlementSource,
    entitlementCheckedAt = entitlementCheckedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = false,
    serverRev = serverRev,
    pendingSync = false,
)
