package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.data.remote.PartnerTransactionImageDto
import com.iponlove.app.feature.transactions.data.remote.TransactionImageDto
import com.iponlove.app.feature.transactions.data.toDomain
import com.iponlove.app.feature.transactions.data.toDto
import com.iponlove.app.feature.transactions.data.toEntity
import org.junit.Test
import java.time.Instant

class TransactionImageMapperTest {

    private val ts = Instant.ofEpochMilli(2_000)

    private fun entity(
        id: String = "img1",
        transactionId: String = "t1",
        url: String? = "https://storage/receipts/u/t1/img1.jpg",
        localPath: String? = null,
        isDeleted: Boolean = false,
        serverRev: Long? = 5,
        pendingSync: Boolean = false,
    ) = TransactionImageEntity(
        id = id, transactionId = transactionId,
        localPath = localPath, url = url, position = 0,
        createdAt = ts, updatedAt = ts,
        isDeleted = isDeleted, serverRev = serverRev, pendingSync = pendingSync,
    )

    private fun dto(
        id: String = "img1",
        storageUrl: String = "https://storage/receipts/u/t1/img1.jpg",
        isDeleted: Boolean = false,
        serverRev: Long? = 5,
    ) = TransactionImageDto(
        id = id, transactionId = "t1", storageUrl = storageUrl,
        position = 0, createdAt = ts, updatedAt = ts,
        isDeleted = isDeleted, serverRev = serverRev,
    )

    @Test
    fun entityToDomain_mapsIdTransactionIdLocalPathUrl() {
        val domain = entity(id = "img1", transactionId = "t1", url = "https://x").toDomain()

        assertThat(domain.id).isEqualTo("img1")
        assertThat(domain.transactionId).isEqualTo("t1")
        assertThat(domain.url).isEqualTo("https://x")
        assertThat(domain.localPath).isNull()
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val entity = dto(serverRev = 7).toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.localPath).isNull()
    }

    @Test
    fun entityToDto_roundTrips_omitsPendingSync() {
        val original = entity(url = "https://storage/receipts/u/t1/img1.jpg", pendingSync = true)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }

    @Test
    fun entityToDto_onUnuploadedRow_throws() {
        val unuploaded = entity(url = null, localPath = "/data/receipts/img1.jpg")

        val error = runCatching { unuploaded.toDto() }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun partnerDtoToEntity_nullUrl_isPreserved_forPurgeSignal() {
        // The partner view nulls storage_url once the parent transaction is private/deleted; the
        // syncer purges on a null url, so the mapper must carry the null through faithfully.
        val partnerDto = PartnerTransactionImageDto(
            id = "img2", transactionId = "t1", storageUrl = null,
            position = 0, isDeleted = false, updatedAt = ts, serverRev = 3,
        )

        val entity = partnerDto.toEntity()

        assertThat(entity.url).isNull()
        assertThat(entity.createdAt).isEqualTo(Instant.EPOCH)
        assertThat(entity.pendingSync).isFalse()
    }

    @Test
    fun partnerDtoToEntity_withUrl_preservesUrl() {
        val partnerDto = PartnerTransactionImageDto(
            id = "img3", transactionId = "t1", storageUrl = "https://storage/partner.jpg",
            position = 1, isDeleted = false, updatedAt = ts, serverRev = 4,
        )

        assertThat(partnerDto.toEntity().url).isEqualTo("https://storage/partner.jpg")
    }
}
