package com.iponlove.app.feature.accounts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.accounts.data.toDomain
import com.iponlove.app.feature.accounts.data.toDto
import com.iponlove.app.feature.accounts.data.toEntity
import org.junit.Test
import java.time.Instant

class AccountMapperTest {

    @Test
    fun entityToDomain_dropsOwnershipAndSyncColumns() {
        val domain = accountEntity(id = "a", name = "Wallet").toDomain()

        assertThat(domain.id).isEqualTo("a")
        assertThat(domain.name).isEqualTo("Wallet")
        // Domain has no userId / updatedAt / pendingSync to leak — covered by it compiling.
    }

    @Test
    fun entityToDto_carriesServerRev_andOmitsPendingSyncByConstruction() {
        val dto = accountEntity(id = "a", serverRev = 42, pendingSync = true).toDto()

        assertThat(dto.id).isEqualTo("a")
        assertThat(dto.serverRev).isEqualTo(42)
        // AccountDto has no pendingSync field at all — it can never go over the wire.
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse_andKeepsServerRev() {
        val entity = accountDto(id = "a", serverRev = 7, updatedAt = Instant.ofEpochMilli(5_000))
            .toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.updatedAt).isEqualTo(Instant.ofEpochMilli(5_000))
    }

    @Test
    fun entityToDto_roundTrips() {
        val original = accountEntity(id = "a", name = "BPI", serverRev = 9)

        val roundTripped = original.toDto().toEntity()

        // pendingSync is intentionally reset to false on the way back from the wire.
        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun coupleOwnedEntity_isShared_andRoundTripsCoupleColumns() {
        val original = accountEntity(
            id = "a", userId = null, coupleId = "couple-1", createdBy = "user-1", serverRev = 9,
        )

        assertThat(original.toDomain().isShared).isTrue()
        // couple_id + created_by survive the wire round-trip (ADR-0018).
        assertThat(original.toDto().toEntity()).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun personalEntity_isNotShared() {
        assertThat(accountEntity(id = "a").toDomain().isShared).isFalse()
    }
}
