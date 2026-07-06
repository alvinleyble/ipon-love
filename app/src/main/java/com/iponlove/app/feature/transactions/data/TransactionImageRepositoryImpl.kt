package com.iponlove.app.feature.transactions.data

import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class TransactionImageRepositoryImpl @Inject constructor(
    private val dao: TransactionImageDao,
    private val clock: SyncClock,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : TransactionImageRepository {

    override suspend fun getImages(transactionId: String): List<TransactionImage> =
        dao.getByTransactionId(transactionId).map { it.toDomain() }

    override fun observeImageUrls(): Flow<Map<String, List<String>>> =
        dao.observeAllUploaded().map { rows ->
            rows.sortedWith(compareBy({ it.position }, { it.createdAt }))
                .groupBy({ it.transactionId }, { it.url!! })
        }

    override suspend fun addImage(transactionId: String, imageId: String, localPath: String) {
        // Data-layer backstop for the 3-cap: never persist a 4th active image for a transaction.
        if (dao.countActiveByTransactionId(transactionId) >= TransactionImage.MAX) return
        val now = clock.stamp(null)
        val position = dao.countActiveByTransactionId(transactionId)
        dao.upsert(
            TransactionImageEntity(
                id = imageId,
                transactionId = transactionId,
                localPath = localPath,
                url = null,
                position = position,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteImage(id: String) {
        val existing = dao.getById(id) ?: return
        if (existing.url == null) {
            // Never uploaded — it never needs to reach the server, so hard-delete + drop the file.
            existing.localPath?.let { File(it).delete() }
            dao.deleteById(id)
        } else {
            dao.upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(existing.updatedAt),
                    pendingSync = true,
                ),
            )
        }
        syncTrigger.requestPush()
    }

    override suspend fun purgePartnerData(userId: String) = dao.deleteNotOwnedByUser(userId)
}
