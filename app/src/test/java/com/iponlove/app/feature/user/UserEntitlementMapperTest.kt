package com.iponlove.app.feature.user

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.toDto
import com.iponlove.app.feature.user.data.toEntity
import org.junit.Test
import java.time.Instant

/** Entitlement columns must round-trip through sync (Entity↔Dto) so a purchase/grant reaches
 *  the partner (D2 / ADR-0044) and self-push never nulls them. */
class UserEntitlementMapperTest {

    private val ts = Instant.ofEpochMilli(1_000)
    private val premiumTs = Instant.ofEpochMilli(9_999)

    private fun entity(
        isPremium: Boolean = false,
        premiumUntil: Instant? = null,
        entitlementSource: String = "NONE",
        entitlementCheckedAt: Instant? = null,
    ) = UserEntity(
        id = "u1",
        displayName = "Alvin",
        avatarUrl = null,
        accentColor = null,
        coupleId = null,
        isPremium = isPremium,
        premiumUntil = premiumUntil,
        entitlementSource = entitlementSource,
        entitlementCheckedAt = entitlementCheckedAt,
        createdAt = ts,
        updatedAt = ts,
        isDeleted = false,
        serverRev = 5,
        pendingSync = false,
    )

    @Test
    fun defaults_areDormant() {
        val e = entity()
        assertThat(e.isPremium).isFalse()
        assertThat(e.premiumUntil).isNull()
        assertThat(e.entitlementSource).isEqualTo("NONE")
        assertThat(e.entitlementCheckedAt).isNull()
    }

    @Test
    fun entityToDto_carriesEntitlement() {
        val dto = entity(
            isPremium = true,
            premiumUntil = premiumTs,
            entitlementSource = "PLAY",
            entitlementCheckedAt = ts,
        ).toDto()

        assertThat(dto.isPremium).isTrue()
        assertThat(dto.premiumUntil).isEqualTo(premiumTs)
        assertThat(dto.entitlementSource).isEqualTo("PLAY")
        assertThat(dto.entitlementCheckedAt).isEqualTo(ts)
    }

    @Test
    fun grant_roundTrips_entityToDtoToEntity() {
        // A beta comp: is_premium=true, never-expires, source=GRANT (ADR-0044 §4).
        val original = entity(isPremium = true, premiumUntil = null, entitlementSource = "GRANT")

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val dto = UserDto(
            id = "partner",
            displayName = "Patty",
            avatarUrl = null,
            accentColor = null,
            coupleId = "c1",
            isPremium = true,
            premiumUntil = null,
            entitlementSource = "PLAY",
            entitlementCheckedAt = ts,
            createdAt = ts,
            updatedAt = ts,
            serverRev = 7,
        )

        val entity = dto.toEntity()

        assertThat(entity.isPremium).isTrue()
        assertThat(entity.entitlementSource).isEqualTo("PLAY")
        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
    }
}
