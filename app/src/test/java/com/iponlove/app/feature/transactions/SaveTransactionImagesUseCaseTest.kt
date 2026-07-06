package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransactionImagesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveTransactionImagesUseCaseTest {

    /** Models the real repository's active-count cap so reconcile ordering is actually exercised. */
    private class FakeRepo(existing: List<TransactionImage>) : TransactionImageRepository {
        private val active = existing.associateBy { it.id }.toMutableMap()
        val added = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        override suspend fun getImages(transactionId: String) =
            active.values.filter { it.transactionId == transactionId }

        override fun observeImageUrls(): Flow<Map<String, List<String>>> = flowOf(emptyMap())

        override suspend fun addImage(transactionId: String, imageId: String, localPath: String) {
            if (active.values.count { it.transactionId == transactionId } >= TransactionImage.MAX) return
            active[imageId] = TransactionImage(imageId, transactionId, localPath = localPath)
            added += imageId
        }

        override suspend fun deleteImage(id: String) { active.remove(id); deleted += id }

        override suspend fun purgePartnerData(userId: String) {}
    }

    private fun draftPick(id: String) = TransactionImage(id = id, transactionId = "t1", localPath = "/f/$id.jpg")
    private fun existingImage(id: String) = TransactionImage(id = id, transactionId = "t1", url = "https://x/$id.jpg")

    @Test
    fun newTransaction_insertsEveryDraftImage() = runTest {
        val repo = FakeRepo(existing = emptyList())

        SaveTransactionImagesUseCase(repo)("t1", listOf(draftPick("a"), draftPick("b")))

        assertThat(repo.added).containsExactly("a", "b")
        assertThat(repo.deleted).isEmpty()
    }

    @Test
    fun edit_insertsNewPicks_andDeletesRemoved_leavesUnchangedAlone() = runTest {
        // Existing [a, b]; the draft keeps a, drops b, and adds a new pick c.
        val repo = FakeRepo(existing = listOf(existingImage("a"), existingImage("b")))

        SaveTransactionImagesUseCase(repo)("t1", listOf(existingImage("a"), draftPick("c")))

        assertThat(repo.added).containsExactly("c")   // a is already persisted → not re-added
        assertThat(repo.deleted).containsExactly("b")  // removed from the draft
    }

    @Test
    fun cap_neverInsertsMoreThanMax_evenIfDraftHasMore() = runTest {
        val repo = FakeRepo(existing = emptyList())
        val fourPicks = listOf(draftPick("a"), draftPick("b"), draftPick("c"), draftPick("d"))

        SaveTransactionImagesUseCase(repo)("t1", fourPicks)

        assertThat(repo.added).hasSize(TransactionImage.MAX)
        assertThat(repo.added).containsExactly("a", "b", "c")
    }

    @Test
    fun swapAtCap_removesOneAndAddsOne_bothApplied() = runTest {
        // Already at the 3-cap [a, b, c]; the draft drops a and adds a new pick d → still 3.
        // Deletes must run before inserts, else the repo cap would count a and drop d.
        val repo = FakeRepo(existing = listOf(existingImage("a"), existingImage("b"), existingImage("c")))

        SaveTransactionImagesUseCase(repo)("t1", listOf(existingImage("b"), existingImage("c"), draftPick("d")))

        assertThat(repo.deleted).containsExactly("a")
        assertThat(repo.added).containsExactly("d")
    }
}
