package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.data.toDomain
import com.iponlove.app.feature.notes.data.toDto
import com.iponlove.app.feature.notes.data.toEntity
import org.junit.Test

class NoteMapperTest {

    @Test
    fun entityToDomain_dropsSyncColumns_carriesShareAndUpdatedAt() {
        val domain = noteEntity(id = "n", title = "Groceries", content = "<p>milk</p>", isShared = true).toDomain()

        assertThat(domain.id).isEqualTo("n")
        assertThat(domain.title).isEqualTo("Groceries")
        assertThat(domain.contentHtml).isEqualTo("<p>milk</p>")
        assertThat(domain.isShared).isTrue()
    }

    @Test
    fun entityToDomain_normalizesNullTitleAndContentToEmpty() {
        val domain = noteEntity(id = "n", title = null, content = null).toDomain()

        assertThat(domain.title).isEmpty()
        assertThat(domain.contentHtml).isEmpty()
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = noteDto(id = "n", serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
    }

    @Test
    fun entityToDto_roundTrips_omitsPendingSync() {
        val original = noteEntity(id = "n", serverRev = 9, coupleId = "c-1", isShared = true)

        val roundTripped = original.toDto().toEntity()

        // The DTO has no pendingSync field, so a pulled row is always server-canonical.
        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }
}
