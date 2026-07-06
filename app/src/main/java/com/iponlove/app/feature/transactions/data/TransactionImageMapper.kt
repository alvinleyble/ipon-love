package com.iponlove.app.feature.transactions.data

import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.data.remote.PartnerTransactionImageDto
import com.iponlove.app.feature.transactions.data.remote.TransactionImageDto
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import java.time.Instant

/** Entity ↔ Domain ↔ DTO conversions for transaction receipt images. Pure functions — unit-tested. */

fun TransactionImageEntity.toDomain(): TransactionImage = TransactionImage(
    id = id,
    transactionId = transactionId,
    localPath = localPath,
    url = url,
    position = position,
)

fun TransactionImageEntity.toDto(): TransactionImageDto = TransactionImageDto(
    id = id,
    transactionId = transactionId,
    storageUrl = url ?: error("toDto called on un-uploaded transaction image $id"),
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

fun TransactionImageDto.toEntity(): TransactionImageEntity = TransactionImageEntity(
    id = id,
    transactionId = transactionId,
    localPath = null,
    url = storageUrl,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)

fun PartnerTransactionImageDto.toEntity(): TransactionImageEntity = TransactionImageEntity(
    id = id,
    transactionId = transactionId,
    localPath = null,
    url = storageUrl,
    position = position,
    createdAt = Instant.EPOCH, // not exposed by the partner view
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
