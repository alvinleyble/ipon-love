package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class TransactionImageUploaderTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dao = object : TransactionImageDao {
        val store = mutableMapOf<String, TransactionImageEntity>()
        var pendingList: List<TransactionImageEntity> = emptyList()
        val urlsMarked = mutableMapOf<String, String>()
        val deleted = mutableListOf<String>()

        override suspend fun getByTransactionId(transactionId: String) = emptyList<TransactionImageEntity>()
        override fun observeAllUploaded(): Flow<List<TransactionImageEntity>> = emptyFlow()
        override suspend fun getById(id: String) = store[id]
        override suspend fun allIds(): List<String> = store.keys.toList()
        override suspend fun countActiveByTransactionId(transactionId: String) = 0
        override suspend fun upsert(entity: TransactionImageEntity) { store[entity.id] = entity }
        override suspend fun deleteById(id: String) { store.remove(id); deleted += id }
        override suspend fun deleteNotOwnedByUser(userId: String) {}
        override suspend fun pendingUploads() = pendingList
        override suspend fun markUploaded(id: String, url: String) { urlsMarked[id] = url }
        override suspend fun dirtyRows() = emptyList<TransactionImageEntity>()
        override suspend fun clearPending(ids: List<String>) {}
        override suspend fun applyPullBatch(entities: List<TransactionImageEntity>) {}
    }

    private fun entity(
        id: String,
        localPath: String?,
        isDeleted: Boolean = false,
    ) = TransactionImageEntity(
        id = id, transactionId = "t1",
        localPath = localPath, url = null, position = 0,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        isDeleted = isDeleted, serverRev = null, pendingSync = true,
    )

    @Test
    fun objectPath_isUserFolderThenTransactionThenImage() {
        // folder[1] == userId keeps the receipts owner RLS satisfied; the extra {transactionId}
        // subfolder groups a transaction's several receipts.
        assertThat(TransactionImageUploader.objectPath("user1", "txn9", "img7"))
            .isEqualTo("user1/txn9/img7.jpg")
    }

    @Test
    fun run_deletedEntityWithLocalFile_deletesFileAndHardDeletesRow() = runTest {
        val file = tmpFolder.newFile("deleted.jpg")
        assertThat(file.exists()).isTrue()

        dao.pendingList = listOf(entity("i1", file.absolutePath, isDeleted = true))

        val uploader = TransactionImageUploader(dao, mockk(relaxed = true), mockk {
            coEvery { userId() } returns "user1"
        })
        uploader.run()

        assertThat(file.exists()).isFalse()
        assertThat(dao.deleted).contains("i1")
    }

    @Test
    fun run_missingLocalFile_skipsRow() = runTest {
        dao.pendingList = listOf(entity("i2", "/nonexistent/file.jpg"))

        val uploader = TransactionImageUploader(dao, mockk(relaxed = true), mockk {
            coEvery { userId() } returns "user1"
        })
        uploader.run()

        assertThat(dao.urlsMarked).isEmpty()
        assertThat(dao.deleted).isEmpty()
    }
}
