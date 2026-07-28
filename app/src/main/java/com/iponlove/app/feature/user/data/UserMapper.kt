package com.iponlove.app.feature.user.data

import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.entitlement.EntitlementSource
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.remote.UserEntitlementWrite
import com.iponlove.app.feature.user.data.remote.UserPushDto
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
    avatarMotif = avatarMotif,
    coupleId = coupleId,
    createdAt = createdAt,
)

/**
 * The ordinary push payload — deliberately **without** the four entitlement columns, which the
 * database refuses to let a client write (ADR-0060). Omitting them is what keeps normal profile
 * edits working: the privilege check rejects a statement that merely *names* a locked column.
 */
fun UserEntity.toPushDto(): UserPushDto = UserPushDto(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    accentColor = accentColor,
    avatarMotif = avatarMotif,
    coupleId = coupleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    serverRev = serverRev,
)

/** The same row's entitlement half, bound for the `set_self_entitlement` RPC (ADR-0060). */
fun UserEntity.toEntitlementWrite(): UserEntitlementWrite = UserEntitlementWrite(
    isPremium = isPremium,
    premiumUntil = premiumUntil,
    source = entitlementSource,
    checkedAt = entitlementCheckedAt,
    updatedAt = updatedAt,
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    accentColor = accentColor,
    avatarMotif = avatarMotif,
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
