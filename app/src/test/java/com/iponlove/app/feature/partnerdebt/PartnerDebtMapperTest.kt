package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.data.toDomain
import com.iponlove.app.feature.partnerdebt.data.toDto
import com.iponlove.app.feature.partnerdebt.data.toEntity
import java.time.Instant
import org.junit.Test

class PartnerDebtMapperTest {

    @Test
    fun debtEntity_toDto_dropsPendingSync_keepsOwnershipAndSyncColumns() {
        val entity = partnerDebtEntity(
            id = "d",
            coupleId = "c-1",
            borrowerId = "me",
            lenderId = "you",
            amount = "1234.50",
            serverRev = 42,
            pendingSync = true,
        )

        val dto = entity.toDto()

        assertThat(dto.id).isEqualTo("d")
        assertThat(dto.coupleId).isEqualTo("c-1")
        assertThat(dto.borrowerId).isEqualTo("me")
        assertThat(dto.lenderId).isEqualTo("you")
        assertThat(dto.amount.toPlainString()).isEqualTo("1234.50")
        assertThat(dto.serverRev).isEqualTo(42)
    }

    @Test
    fun debtDto_toEntity_marksClean_andCarriesServerRev() {
        val entity = partnerDebtDto(id = "d", amount = "10.00", serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.amount.toPlainString()).isEqualTo("10.00")
    }

    @Test
    fun debtEntity_toDomain_exposesBorrowerLenderAndAmount() {
        val domain = partnerDebtEntity(id = "d", borrowerId = "a", lenderId = "b", amount = "55.00").toDomain()

        assertThat(domain.borrowerId).isEqualTo("a")
        assertThat(domain.lenderId).isEqualTo("b")
        assertThat(domain.amount.toPlainString()).isEqualTo("55.00")
    }

    @Test
    fun debt_roundTrips_sourceTransactionId() {
        val entity = partnerDebtEntity(id = "d", sourceTransactionId = "txn-7")

        assertThat(entity.toDto().sourceTransactionId).isEqualTo("txn-7")
        assertThat(entity.toDomain().sourceTransactionId).isEqualTo("txn-7")
        assertThat(partnerDebtDto(id = "d", sourceTransactionId = "txn-7").toEntity().sourceTransactionId)
            .isEqualTo("txn-7")
        // Manual debts carry no source link.
        assertThat(partnerDebtEntity(id = "m").toDomain().sourceTransactionId).isNull()
    }

    @Test
    fun payment_roundTrips_throughDtoAndEntity() {
        val entity = debtPaymentEntity(
            id = "p",
            debtId = "d",
            amount = "99.99",
            note = "gcash",
            date = Instant.ofEpochMilli(5_000),
            serverRev = 3,
            pendingSync = true,
        )

        val dto = entity.toDto()
        assertThat(dto.debtId).isEqualTo("d")
        assertThat(dto.note).isEqualTo("gcash")
        assertThat(dto.date).isEqualTo(Instant.ofEpochMilli(5_000))

        val back = dto.toEntity()
        assertThat(back.pendingSync).isFalse()
        assertThat(back.amount.toPlainString()).isEqualTo("99.99")
        assertThat(back.serverRev).isEqualTo(3)
    }

    @Test
    fun nettingPayment_roundTrips_isNettingAndCounterDebtId() {
        val entity = debtPaymentEntity(
            id = "np",
            debtId = "d-new",
            amount = "500.00",
            isNetting = true,
            counterDebtId = "d-old",
        )

        val dto = entity.toDto()
        assertThat(dto.isNetting).isTrue()
        assertThat(dto.counterDebtId).isEqualTo("d-old")

        val domain = entity.toDomain()
        assertThat(domain.isNetting).isTrue()
        assertThat(domain.counterDebtId).isEqualTo("d-old")

        val back = dto.toEntity()
        assertThat(back.isNetting).isTrue()
        assertThat(back.counterDebtId).isEqualTo("d-old")
        assertThat(back.pendingSync).isFalse()
    }

    @Test
    fun settlementPayment_roundTrips_payorAndReceiverLinks() {
        val entity = debtPaymentEntity(
            id = "sp",
            debtId = "d-1",
            amount = "500.00",
            payorAccountId = "acc-1",
            payorTxnId = "txn-pay",
            receiverTxnId = "txn-recv",
        )

        val dto = entity.toDto()
        assertThat(dto.payorAccountId).isEqualTo("acc-1")
        assertThat(dto.payorTxnId).isEqualTo("txn-pay")
        assertThat(dto.receiverTxnId).isEqualTo("txn-recv")

        val domain = entity.toDomain()
        assertThat(domain.payorAccountId).isEqualTo("acc-1")
        assertThat(domain.payorTxnId).isEqualTo("txn-pay")
        assertThat(domain.receiverTxnId).isEqualTo("txn-recv")

        val back = dto.toEntity()
        assertThat(back.payorAccountId).isEqualTo("acc-1")
        assertThat(back.payorTxnId).isEqualTo("txn-pay")
        assertThat(back.receiverTxnId).isEqualTo("txn-recv")
        assertThat(back.pendingSync).isFalse()
    }

    @Test
    fun manualPayment_isNettingFalse_counterDebtIdNull() {
        val entity = debtPaymentEntity(id = "mp", debtId = "d")

        assertThat(entity.isNetting).isFalse()
        assertThat(entity.counterDebtId).isNull()
        assertThat(entity.toDto().isNetting).isFalse()
        assertThat(entity.toDto().counterDebtId).isNull()
        assertThat(entity.toDomain().isNetting).isFalse()
        assertThat(entity.toDomain().counterDebtId).isNull()
    }
}
