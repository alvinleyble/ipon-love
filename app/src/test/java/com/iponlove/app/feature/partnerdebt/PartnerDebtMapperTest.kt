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
}
