package com.iponlove.app.feature.notifications

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notifications.data.toDomain
import com.iponlove.app.feature.notifications.data.toDto
import com.iponlove.app.feature.notifications.data.toEntity
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import org.junit.Test
import java.time.Instant

class NotificationMapperTest {

    @Test
    fun toDto_dropsPendingSync_andKeepsEveryWireField() {
        val dto = notificationEntity(
            id = "budget:b1:2026-07:warn",
            serverRev = 42,
            pendingSync = true,
            isRead = true,
        ).toDto()

        assertThat(dto.id).isEqualTo("budget:b1:2026-07:warn")
        assertThat(dto.userId).isEqualTo("user-1")
        assertThat(dto.category).isEqualTo("budget")
        assertThat(dto.deepLink).isEqualTo("manage")
        assertThat(dto.isRead).isTrue()
        assertThat(dto.serverRev).isEqualTo(42)
    }

    @Test
    fun toEntity_marksAPulledRowClean() {
        val entity = notificationDto("recurring:occ-1", category = "recurring", serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.category).isEqualTo("recurring")
    }

    @Test
    fun entityDtoRoundTripPreservesEveryField() {
        val original = notificationEntity(
            id = "debt:d1",
            category = "couple",
            isRead = true,
            createdAt = Instant.ofEpochMilli(5_000),
            updatedAt = Instant.ofEpochMilli(6_000),
            serverRev = 9,
        )

        assertThat(original.toDto().toEntity()).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun toDomain_resolvesTheCategoryKey() {
        assertThat(notificationEntity("a", category = "budget").toDomain().category)
            .isEqualTo(NotificationCategory.BUDGET)
        assertThat(notificationEntity("a", category = "recurring").toDomain().category)
            .isEqualTo(NotificationCategory.RECURRING)
        assertThat(notificationEntity("a", category = "couple").toDomain().category)
            .isEqualTo(NotificationCategory.COUPLE)
    }

    /**
     * A newer client (the coming web app) may write a category this build doesn't know. The row
     * must still surface in the inbox rather than being dropped or crashing the mapper.
     */
    @Test
    fun toDomain_fallsBackForAnUnknownCategory() {
        assertThat(notificationEntity("a", category = "something_new").toDomain().category)
            .isEqualTo(NotificationCategory.OTHER)
    }

    /** Keys are embedded in synced ids and rows — a rename would orphan every existing row. */
    @Test
    fun categoryKeysAreStable() {
        assertThat(NotificationCategory.entries.map { it.key })
            .containsExactly("budget", "recurring", "couple", "other")
            .inOrder()
    }
}
