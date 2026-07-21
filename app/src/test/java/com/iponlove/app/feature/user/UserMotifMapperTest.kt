package com.iponlove.app.feature.user

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.toDomain
import com.iponlove.app.feature.user.data.toDto
import com.iponlove.app.feature.user.data.toEntity
import org.junit.Test
import java.time.Instant

/**
 * The `avatar_motif` cosmetic must round-trip through sync (Entity↔Dto) so a partner sees the
 * chosen motif (v1.6.7 Item 3 Leg 1, ADR-0014), and reach the domain via [toDomain]. A self-push
 * must never null it.
 */
class UserMotifMapperTest {

    private val ts = Instant.ofEpochMilli(1_000)

    private fun entity(avatarMotif: String?) = UserEntity(
        id = "u1",
        displayName = "Alvin",
        avatarUrl = null,
        accentColor = "#1565C0",
        avatarMotif = avatarMotif,
        coupleId = null,
        createdAt = ts,
        updatedAt = ts,
        isDeleted = false,
        serverRev = 5,
        pendingSync = false,
    )

    @Test
    fun entityToDto_carriesMotif() {
        assertThat(entity("leaf").toDto().avatarMotif).isEqualTo("leaf")
    }

    @Test
    fun dtoToEntity_carriesMotif() {
        val dto = UserDto(
            id = "u1",
            displayName = "Patty",
            avatarUrl = null,
            accentColor = "#C2185B",
            avatarMotif = "gem",
            coupleId = "c1",
            createdAt = ts,
            updatedAt = ts,
            serverRev = 3,
        )
        assertThat(dto.toEntity().avatarMotif).isEqualTo("gem")
    }

    @Test
    fun entityToDomain_carriesMotif() {
        assertThat(entity("sprout").toDomain().avatarMotif).isEqualTo("sprout")
    }

    @Test
    fun nullMotif_roundTripsAsNull() {
        assertThat(entity(null).toDto().avatarMotif).isNull()
        assertThat(entity(null).toDomain().avatarMotif).isNull()
    }
}
