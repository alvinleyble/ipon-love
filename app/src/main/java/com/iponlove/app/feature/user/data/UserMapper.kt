package com.iponlove.app.feature.user.data

import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.domain.model.User

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
