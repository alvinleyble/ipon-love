package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.data.toDomain
import com.iponlove.app.feature.recurring.data.toDto
import com.iponlove.app.feature.recurring.data.toEntity
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import org.junit.Test
import java.time.LocalDate

class RecurringRuleMapperTest {

    @Test
    fun entityToDomain_unflattensTemplate_dropsSyncColumns() {
        val domain = ruleEntity(
            id = "r",
            frequency = RecurringFrequency.WEEKLY,
            interval = 2,
            amount = "1500.00",
            note = "Allowance",
        ).toDomain()

        assertThat(domain.id).isEqualTo("r")
        assertThat(domain.frequency).isEqualTo(RecurringFrequency.WEEKLY)
        assertThat(domain.interval).isEqualTo(2)
        assertThat(domain.template.amount.toPlainString()).isEqualTo("1500.00")
        assertThat(domain.template.note).isEqualTo("Allowance")
    }

    @Test
    fun entityToDto_nestsTemplate_carriesServerRev_omitsPendingSync() {
        val dto = ruleEntity(id = "r", serverRev = 42, pendingSync = true, amount = "900.00").toDto()

        assertThat(dto.serverRev).isEqualTo(42)
        assertThat(dto.template.amount.toPlainString()).isEqualTo("900.00")
        assertThat(dto.frequency).isEqualTo("MONTHLY")
        // RecurringRuleDto has no pendingSync field — it can never go over the wire.
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = ruleDto(id = "r", serverRev = 7, endDate = LocalDate.of(2027, 1, 1)).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.endDate).isEqualTo(LocalDate.of(2027, 1, 1))
    }

    @Test
    fun entityToDto_roundTrips() {
        val original = ruleEntity(id = "r", serverRev = 9, endDate = LocalDate.of(2027, 3, 1))

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }
}
