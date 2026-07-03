package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.savings.data.toDomain
import com.iponlove.app.feature.savings.data.toDto
import com.iponlove.app.feature.savings.data.toEntity
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SavingsGoalMapperTest {

    @Test
    fun toDomain_flagsPartnerGoalByOwnership() {
        val mine = savingsGoalEntity(id = "g", userId = "user-1").toDomain("user-1")
        val theirs = savingsGoalEntity(id = "g", userId = "partner-1").toDomain("user-1")
        assertThat(mine.isPartnerGoal).isFalse()
        assertThat(theirs.isPartnerGoal).isTrue()
    }

    @Test
    fun entity_toDto_toEntity_roundTrips_clearingPendingSync() {
        val entity = savingsGoalEntity(
            id = "g", targetDate = LocalDate.of(2026, 12, 25), serverRev = 7, pendingSync = true,
        )
        val round = entity.toDto().toEntity()
        // Wire form drops pending_sync; a pulled row is server-canonical (false).
        assertThat(round).isEqualTo(entity.copy(pendingSync = false))
    }

    @Test
    fun partnerDto_visible_mapsFields_createdAtFallsBackToUpdatedAt() {
        val entity = partnerSavingsGoalDto(
            id = "pg", updatedAt = Instant.ofEpochMilli(5_000), targetAmount = BigDecimal("50000.00"),
        ).toEntity()
        assertThat(entity.userId).isEqualTo("partner-1")
        assertThat(entity.name).isEqualTo("Trip to Japan")
        assertThat(entity.targetAmount).isEqualTo(BigDecimal("50000.00"))
        assertThat(entity.isShared).isTrue()
        assertThat(entity.createdAt).isEqualTo(Instant.ofEpochMilli(5_000))
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun partnerDto_redacted_usesSafeDefaults() {
        // A redacted (unshared) partner goal crosses with content nulled — the mapper must not NPE;
        // the syncer purges it anyway (shouldPurge = !isShared || isDeleted).
        val entity = partnerSavingsGoalDto(
            id = "pg", name = null, targetAmount = null, isArchived = null, isShared = false,
        ).toEntity()
        assertThat(entity.name).isEmpty()
        assertThat(entity.targetAmount).isEqualTo(BigDecimal.ZERO)
        assertThat(entity.isArchived).isFalse()
        assertThat(entity.isShared).isFalse()
    }
}
