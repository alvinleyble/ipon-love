package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.data.local.CoupleEntity
import com.iponlove.app.feature.couple.data.remote.CoupleDto
import com.iponlove.app.feature.couple.data.toDomain
import com.iponlove.app.feature.couple.data.toDto
import com.iponlove.app.feature.couple.data.toEntity
import org.junit.Test
import java.time.Instant

class CoupleMapperTest {

    @Test
    fun entityToDomain_exposesPairingFields_andAwaitingPartnerFlag() {
        val domain = coupleEntity(id = "c", user2Id = null).toDomain()

        assertThat(domain.id).isEqualTo("c")
        assertThat(domain.name).isEqualTo("Us")
        assertThat(domain.inviteCode).isEqualTo("ABCD23")
        assertThat(domain.user1Id).isEqualTo("user-1")
        assertThat(domain.user2Id).isNull()
        assertThat(domain.isAwaitingPartner).isTrue()
    }

    @Test
    fun entityToDomain_pairedCouple_isNotAwaitingPartner() {
        val domain = coupleEntity(id = "c", user2Id = "user-2").toDomain()

        assertThat(domain.user2Id).isEqualTo("user-2")
        assertThat(domain.isAwaitingPartner).isFalse()
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = coupleDto(id = "c", serverRev = 7, isDeleted = true).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.isDeleted).isTrue()
    }

    @Test
    fun entityToDto_roundTrips_droppingOnlyPendingSync() {
        val original = coupleEntity(id = "c", user2Id = "user-2", serverRev = 9)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun bannerUrl_roundTrips_throughDomainDtoEntity() {
        val url = "https://x/storage/v1/object/authenticated/couple-banners/c/abc.jpg"
        val entity = coupleEntity(id = "c", user2Id = "user-2", bannerUrl = url)

        assertThat(entity.toDomain().bannerUrl).isEqualTo(url)
        assertThat(entity.toDto().bannerUrl).isEqualTo(url)
        assertThat(entity.toDto().toEntity().bannerUrl).isEqualTo(url)
    }

    @Test
    fun nullBannerUrl_roundTrips_asNull() {
        val entity = coupleEntity(id = "c", user2Id = "user-2", bannerUrl = null)

        assertThat(entity.toDomain().bannerUrl).isNull()
        assertThat(entity.toDto().toEntity().bannerUrl).isNull()
    }
}

private fun coupleEntity(
    id: String,
    user2Id: String? = null,
    serverRev: Long? = null,
    isDeleted: Boolean = false,
    pendingSync: Boolean = false,
    bannerUrl: String? = null,
) = CoupleEntity(
    id = id,
    coupleName = "Us",
    inviteCode = "ABCD23",
    user1Id = "user-1",
    user2Id = user2Id,
    bannerUrl = bannerUrl,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = Instant.ofEpochMilli(2_000),
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

private fun coupleDto(
    id: String,
    serverRev: Long? = null,
    isDeleted: Boolean = false,
) = CoupleDto(
    id = id,
    coupleName = "Us",
    inviteCode = "ABCD23",
    user1Id = "user-1",
    user2Id = "user-2",
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = Instant.ofEpochMilli(2_000),
    isDeleted = isDeleted,
    serverRev = serverRev,
)
