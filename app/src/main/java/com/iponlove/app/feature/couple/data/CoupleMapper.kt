package com.iponlove.app.feature.couple.data

import com.iponlove.app.feature.couple.data.local.CoupleEntity
import com.iponlove.app.feature.couple.data.remote.CoupleDto
import com.iponlove.app.feature.couple.domain.model.Couple

fun CoupleEntity.toDomain(): Couple = Couple(
    id = id,
    name = coupleName,
    inviteCode = inviteCode,
    user1Id = user1Id,
    user2Id = user2Id,
    isDeleted = isDeleted,
    bannerUrl = bannerUrl,
)

fun CoupleEntity.toDto(): CoupleDto = CoupleDto(
    id = id,
    coupleName = coupleName,
    inviteCode = inviteCode,
    user1Id = user1Id,
    user2Id = user2Id,
    bannerUrl = bannerUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

fun CoupleDto.toEntity(): CoupleEntity = CoupleEntity(
    id = id,
    coupleName = coupleName,
    inviteCode = inviteCode,
    user1Id = user1Id,
    user2Id = user2Id,
    bannerUrl = bannerUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
