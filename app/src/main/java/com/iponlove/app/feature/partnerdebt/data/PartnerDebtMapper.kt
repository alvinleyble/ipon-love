package com.iponlove.app.feature.partnerdebt.data

import com.iponlove.app.feature.partnerdebt.data.local.DebtPaymentEntity
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtEntity
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentDto
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtDto
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

// ---- partner_debts ----

fun PartnerDebtEntity.toDomain(): PartnerDebt = PartnerDebt(
    id = id,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = amount,
    description = description,
    createdAt = createdAt,
    sourceTransactionId = sourceTransactionId,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun PartnerDebtEntity.toDto(): PartnerDebtDto = PartnerDebtDto(
    id = id,
    coupleId = coupleId,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = amount,
    description = description,
    sourceTransactionId = sourceTransactionId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/** DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`. */
fun PartnerDebtDto.toEntity(): PartnerDebtEntity = PartnerDebtEntity(
    id = id,
    coupleId = coupleId,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = amount,
    description = description,
    sourceTransactionId = sourceTransactionId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)

// ---- partner_debt_payments ----

fun DebtPaymentEntity.toDomain(): DebtPayment = DebtPayment(
    id = id,
    debtId = debtId,
    amount = amount,
    note = note,
    date = date,
    isNetting = isNetting,
    counterDebtId = counterDebtId,
    payorAccountId = payorAccountId,
    payorTxnId = payorTxnId,
    receiverTxnId = receiverTxnId,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun DebtPaymentEntity.toDto(): DebtPaymentDto = DebtPaymentDto(
    id = id,
    debtId = debtId,
    amount = amount,
    note = note,
    date = date,
    isNetting = isNetting,
    counterDebtId = counterDebtId,
    payorAccountId = payorAccountId,
    payorTxnId = payorTxnId,
    receiverTxnId = receiverTxnId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/** DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`. */
fun DebtPaymentDto.toEntity(): DebtPaymentEntity = DebtPaymentEntity(
    id = id,
    debtId = debtId,
    amount = amount,
    note = note,
    date = date,
    isNetting = isNetting,
    counterDebtId = counterDebtId,
    payorAccountId = payorAccountId,
    payorTxnId = payorTxnId,
    receiverTxnId = receiverTxnId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
