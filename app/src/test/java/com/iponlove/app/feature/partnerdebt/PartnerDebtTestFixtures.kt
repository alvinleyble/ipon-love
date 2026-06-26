package com.iponlove.app.feature.partnerdebt

import com.iponlove.app.feature.partnerdebt.data.local.DebtPaymentEntity
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtEntity
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentDto
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtDto
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** In-memory [PartnerDebtDao] for fast JVM tests — both tables in one fake. */
class FakePartnerDebtDao : PartnerDebtDao {
    val debts = linkedMapOf<String, PartnerDebtEntity>()
    val payments = linkedMapOf<String, DebtPaymentEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeDebts(coupleId: String): Flow<List<PartnerDebtEntity>> =
        changes.map { debts.values.filter { !it.isDeleted && it.coupleId == coupleId } }

    override suspend fun getDebt(id: String): PartnerDebtEntity? = debts[id]

    override suspend fun activeDebts(coupleId: String): List<PartnerDebtEntity> =
        debts.values.filter { !it.isDeleted && it.coupleId == coupleId }

    override suspend fun upsertDebt(debt: PartnerDebtEntity) {
        debts[debt.id] = debt
        changes.value++
    }

    override fun observePayments(): Flow<List<DebtPaymentEntity>> =
        changes.map { payments.values.filter { !it.isDeleted } }

    override suspend fun getPayment(id: String): DebtPaymentEntity? = payments[id]

    override suspend fun activePayments(): List<DebtPaymentEntity> =
        payments.values.filter { !it.isDeleted }

    override suspend fun nettingPaymentsForDebt(debtId: String): List<DebtPaymentEntity> =
        payments.values.filter {
            (it.debtId == debtId || it.counterDebtId == debtId) && it.isNetting && !it.isDeleted
        }

    override suspend fun upsertPayment(payment: DebtPaymentEntity) {
        payments[payment.id] = payment
        changes.value++
    }

    override suspend fun deleteAllDebts() {
        debts.clear()
        changes.value++
    }

    override suspend fun deleteAllPayments() {
        payments.clear()
        changes.value++
    }

    override suspend fun dirtyDebts(): List<PartnerDebtEntity> = debts.values.filter { it.pendingSync }

    override suspend fun clearDebtPending(ids: List<String>) {
        ids.forEach { id -> debts[id]?.let { debts[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyDebtBatch(debts: List<PartnerDebtEntity>) {
        debts.forEach { this.debts[it.id] = it }
        changes.value++
    }

    override suspend fun dirtyPayments(): List<DebtPaymentEntity> = payments.values.filter { it.pendingSync }

    override suspend fun clearPaymentPending(ids: List<String>) {
        ids.forEach { id -> payments[id]?.let { payments[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPaymentBatch(payments: List<DebtPaymentEntity>) {
        payments.forEach { this.payments[it.id] = it }
        changes.value++
    }
}

fun partnerDebt(
    id: String,
    borrowerId: String = "me",
    lenderId: String = "you",
    amount: String = "1000.00",
    description: String? = "dinner",
    createdAt: Instant = Instant.ofEpochMilli(1_000),
) = PartnerDebt(
    id = id,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = BigDecimal(amount),
    description = description,
    createdAt = createdAt,
)

fun debtPayment(
    id: String,
    debtId: String,
    amount: String = "100.00",
    note: String? = null,
    date: Instant = Instant.ofEpochMilli(2_000),
) = DebtPayment(
    id = id,
    debtId = debtId,
    amount = BigDecimal(amount),
    note = note,
    date = date,
)

fun partnerDebtEntity(
    id: String,
    coupleId: String = "c-1",
    borrowerId: String = "me",
    lenderId: String = "you",
    amount: String = "1000.00",
    description: String? = "dinner",
    sourceTransactionId: String? = null,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = PartnerDebtEntity(
    id = id,
    coupleId = coupleId,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = BigDecimal(amount),
    description = description,
    sourceTransactionId = sourceTransactionId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun debtPaymentEntity(
    id: String,
    debtId: String = "d-1",
    amount: String = "100.00",
    note: String? = null,
    date: Instant = Instant.ofEpochMilli(2_000),
    isNetting: Boolean = false,
    counterDebtId: String? = null,
    createdAt: Instant = Instant.ofEpochMilli(2_000),
    updatedAt: Instant = Instant.ofEpochMilli(2_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = DebtPaymentEntity(
    id = id,
    debtId = debtId,
    amount = BigDecimal(amount),
    note = note,
    date = date,
    isNetting = isNetting,
    counterDebtId = counterDebtId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun partnerDebtDto(
    id: String,
    coupleId: String = "c-1",
    borrowerId: String = "me",
    lenderId: String = "you",
    amount: String = "1000.00",
    description: String? = "dinner",
    sourceTransactionId: String? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
) = PartnerDebtDto(
    id = id,
    coupleId = coupleId,
    borrowerId = borrowerId,
    lenderId = lenderId,
    amount = BigDecimal(amount),
    description = description,
    sourceTransactionId = sourceTransactionId,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

fun debtPaymentDto(
    id: String,
    debtId: String = "d-1",
    amount: String = "100.00",
    note: String? = null,
    date: Instant = Instant.ofEpochMilli(2_000),
    isNetting: Boolean = false,
    counterDebtId: String? = null,
    updatedAt: Instant = Instant.ofEpochMilli(2_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
) = DebtPaymentDto(
    id = id,
    debtId = debtId,
    amount = BigDecimal(amount),
    note = note,
    date = date,
    isNetting = isNetting,
    counterDebtId = counterDebtId,
    createdAt = Instant.ofEpochMilli(2_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
