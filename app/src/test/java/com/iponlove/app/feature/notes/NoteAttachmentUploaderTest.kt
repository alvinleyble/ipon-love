package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.upload.NoteAttachmentUploader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class NoteAttachmentUploaderTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dao = object : NoteAttachmentDao {
        val store = mutableMapOf<String, NoteAttachmentEntity>()
        var pendingList: List<NoteAttachmentEntity> = emptyList()
        val urlsMarked = mutableMapOf<String, String>()
        val deleted = mutableListOf<String>()

        override fun observeByNoteId(noteId: String): Flow<List<NoteAttachmentEntity>> = emptyFlow()
        override suspend fun getById(id: String) = store[id]
        override suspend fun countActiveByNoteId(noteId: String) = 0
        override suspend fun upsert(entity: NoteAttachmentEntity) { store[entity.id] = entity }
        override suspend fun deleteById(id: String) { store.remove(id); deleted += id }
        override suspend fun deleteNotOwnedByUser(userId: String) {}
        override suspend fun pendingUploads() = pendingList
        override suspend fun markUploaded(id: String, url: String) { urlsMarked[id] = url }
        override suspend fun dirtyRows() = emptyList<NoteAttachmentEntity>()
        override suspend fun clearPending(ids: List<String>) {}
        override suspend fun applyPullBatch(entities: List<NoteAttachmentEntity>) {}
        override suspend fun softDeleteAllForNote(noteId: String, updatedAt: Instant) {}
    }

    private fun entity(
        id: String,
        localPath: String?,
        isDeleted: Boolean = false,
    ) = NoteAttachmentEntity(
        id = id, noteId = "note1", type = "IMAGE",
        localPath = localPath, url = null, position = 0,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        isDeleted = isDeleted, serverRev = null, pendingSync = true,
    )

    @Test
    fun run_deletedEntityWithLocalFile_deletesFileAndHardDeletesRow() = runTest {
        val file = tmpFolder.newFile("deleted.jpg")
        assertThat(file.exists()).isTrue()

        dao.pendingList = listOf(entity("a1", file.absolutePath, isDeleted = true))

        val uploader = NoteAttachmentUploader(dao, mockk(relaxed = true), mockk {
            coEvery { userId() } returns "user1"
        })
        uploader.run()

        assertThat(file.exists()).isFalse()
        assertThat(dao.deleted).contains("a1")
    }

    @Test
    fun run_missingLocalFile_skipsRow() = runTest {
        dao.pendingList = listOf(entity("a2", "/nonexistent/file.jpg"))

        val uploader = NoteAttachmentUploader(dao, mockk(relaxed = true), mockk {
            coEvery { userId() } returns "user1"
        })
        uploader.run()

        assertThat(dao.urlsMarked).isEmpty()
        assertThat(dao.deleted).isEmpty()
    }

    @Test
    fun run_nullLocalPath_skipsRow() = runTest {
        dao.pendingList = listOf(entity("a3", null))

        val uploader = NoteAttachmentUploader(dao, mockk(relaxed = true), mockk {
            coEvery { userId() } returns "user1"
        })
        uploader.run()

        assertThat(dao.urlsMarked).isEmpty()
    }
}
