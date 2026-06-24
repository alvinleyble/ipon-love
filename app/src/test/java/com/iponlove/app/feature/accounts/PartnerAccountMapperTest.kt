package com.iponlove.app.feature.accounts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.data.remote.PartnerAccountDto
import com.iponlove.app.feature.accounts.data.toEntity
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

class PartnerAccountMapperTest {

    @Test
    fun visibleRow_mapsMetadata_butAlwaysZeroOpeningBalance() {
        // opening_balance is absent from partner_accounts — partner balances aren't shown (ADR-0011).
        val entity = partnerDto(name = "Partner GCash", isDeleted = false, serverRev = 3).toEntity()

        assertThat(entity.userId).isEqualTo("partner-1")
        assertThat(entity.name).isEqualTo("Partner GCash")
        assertThat(entity.type).isEqualTo(AccountType.EWALLET)
        assertThat(entity.openingBalance).isEqualTo(BigDecimal.ZERO)
        assertThat(entity.serverRev).isEqualTo(3)
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun deletedRow_nullContent_mapsToSafeDefaults() {
        val entity = partnerDto(name = null, type = null, isDeleted = true, serverRev = 4).toEntity()

        assertThat(entity.name).isEqualTo("")
        assertThat(entity.type).isEqualTo(AccountType.CASH)
        assertThat(entity.isDeleted).isTrue()
    }
}

private fun partnerDto(
    name: String?,
    type: AccountType? = AccountType.EWALLET,
    isDeleted: Boolean,
    serverRev: Long?,
) = PartnerAccountDto(
    id = "acc-p",
    userId = "partner-1",
    name = name,
    type = type,
    icon = null,
    color = null,
    isArchived = false,
    isDeleted = isDeleted,
    updatedAt = Instant.ofEpochMilli(2_000),
    serverRev = serverRev,
)
