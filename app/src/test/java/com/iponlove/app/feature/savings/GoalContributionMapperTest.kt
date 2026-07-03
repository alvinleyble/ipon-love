package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.savings.data.toDomain
import com.iponlove.app.feature.savings.data.toDto
import com.iponlove.app.feature.savings.data.toEntity
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalContributionMapperTest {

    @Test
    fun toDomain_flagsMineByContributor() {
        val mine = goalContributionEntity(id = "c", userId = "user-1").toDomain("user-1")
        val theirs = goalContributionEntity(id = "c", userId = "partner-1").toDomain("user-1")
        assertThat(mine.isMine).isTrue()
        assertThat(mine.byUserId).isEqualTo("user-1")
        assertThat(theirs.isMine).isFalse()
    }

    @Test
    fun entity_toDto_toEntity_roundTrips_clearingPendingSync() {
        val entity = goalContributionEntity(id = "c", serverRev = 3, pendingSync = true, note = "gift")
        assertThat(entity.toDto().toEntity()).isEqualTo(entity.copy(pendingSync = false))
    }

    @Test
    fun partnerDto_visible_mapsAmount_notPurged() {
        val entity = partnerGoalContributionDto(id = "c", amount = BigDecimal("750.00")).toEntity()
        assertThat(entity.amount).isEqualTo(BigDecimal("750.00"))
        assertThat(entity.isDeleted).isFalse()
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun partnerDto_redactedAmount_foldsIntoIsDeleted_forPurge() {
        // Null amount ⇒ the parent goal was unshared/deleted (or the row deleted). Folding it into
        // isDeleted lets the partner syncer purge it uniformly (shouldPurge = isDeleted).
        val entity = partnerGoalContributionDto(id = "c", amount = null, date = null).toEntity()
        assertThat(entity.isDeleted).isTrue()
        assertThat(entity.amount).isEqualTo(BigDecimal.ZERO)
        assertThat(entity.date).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun partnerDto_authorDeleted_isPurged() {
        val entity = partnerGoalContributionDto(id = "c", isDeleted = true).toEntity()
        assertThat(entity.isDeleted).isTrue()
    }
}
