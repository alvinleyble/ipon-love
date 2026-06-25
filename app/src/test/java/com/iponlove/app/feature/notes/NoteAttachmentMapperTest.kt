package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.data.toDomain
import com.iponlove.app.feature.notes.data.toDto
import com.iponlove.app.feature.notes.data.toEntity
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.remote.NoteAttachmentDto
import com.iponlove.app.feature.notes.data.remote.PartnerNoteAttachmentDto
import org.junit.Test
import java.time.Instant

class NoteAttachmentMapperTest {

    private val ts = Instant.ofEpochMilli(2_000)

    private fun entity(
        id: String = "a1",
        noteId: String = "n1",
        url: String? = "https://storage/path.jpg",
        localPath: String? = null,
        isDeleted: Boolean = false,
        serverRev: Long? = 5,
        pendingSync: Boolean = false,
    ) = NoteAttachmentEntity(
        id = id, noteId = noteId, type = "IMAGE",
        localPath = localPath, url = url, position = 0,
        createdAt = ts, updatedAt = ts,
        isDeleted = isDeleted, serverRev = serverRev, pendingSync = pendingSync,
    )

    private fun dto(
        id: String = "a1",
        storageUrl: String = "https://storage/path.jpg",
        isDeleted: Boolean = false,
        serverRev: Long? = 5,
    ) = NoteAttachmentDto(
        id = id, noteId = "n1", storageUrl = storageUrl,
        position = 0, createdAt = ts, updatedAt = ts,
        isDeleted = isDeleted, serverRev = serverRev,
    )

    @Test
    fun entityToDomain_mapsIdNoteIdLocalPathUrl() {
        val domain = entity(id = "a1", noteId = "n1", url = "https://x").toDomain()

        assertThat(domain.id).isEqualTo("a1")
        assertThat(domain.noteId).isEqualTo("n1")
        assertThat(domain.url).isEqualTo("https://x")
        assertThat(domain.localPath).isNull()
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse_typeImage() {
        val entity = dto(serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.type).isEqualTo("IMAGE")
        assertThat(entity.localPath).isNull()
    }

    @Test
    fun entityToDto_roundTrips_omitsPendingSync() {
        val original = entity(url = "https://storage/path.jpg", pendingSync = true)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun partnerDtoToEntity_defaultsCreatedAtToEpoch_nullUrlPreserved() {
        val partnerDto = PartnerNoteAttachmentDto(
            id = "a2", noteId = "n1", storageUrl = null,
            position = 0, isDeleted = false, updatedAt = ts, serverRev = 3,
        )

        val entity = partnerDto.toEntity()

        assertThat(entity.url).isNull()
        assertThat(entity.createdAt).isEqualTo(Instant.EPOCH)
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun partnerDtoToEntity_withUrl_preservesUrl() {
        val partnerDto = PartnerNoteAttachmentDto(
            id = "a3", noteId = "n1", storageUrl = "https://storage/partner.jpg",
            position = 1, isDeleted = false, updatedAt = ts, serverRev = 4,
        )

        assertThat(partnerDto.toEntity().url).isEqualTo("https://storage/partner.jpg")
    }
}
