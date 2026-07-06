package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import javax.inject.Inject

/**
 * Reconciles a transaction's receipt images to the editor's [desired] set on save (the editor
 * defers all persistence to save, unlike notes). New picks — those not yet persisted, which
 * carry a `localPath` — are inserted; images the user removed are deleted. Capped at
 * [TransactionImage.MAX] as a backstop even if the UI let more through.
 *
 * Pure delegation to the repository so it's JVM-unit-testable with a fake repository.
 */
class SaveTransactionImagesUseCase @Inject constructor(
    private val repository: TransactionImageRepository,
) {
    suspend operator fun invoke(transactionId: String, desired: List<TransactionImage>) {
        val capped = desired.take(TransactionImage.MAX)
        val existing = repository.getImages(transactionId)
        val existingIds = existing.map { it.id }.toSet()
        val desiredIds = capped.map { it.id }.toSet()

        // Deletes first: the repository's active-count cap must not count a soon-to-be-removed
        // image against a newly-added one (swapping an image at the 3-cap would otherwise drop it).
        for (image in existing) {
            if (image.id !in desiredIds) repository.deleteImage(image.id)
        }
        // Then insert newly-picked images (present in the draft, not yet persisted, with a local file).
        for (image in capped) {
            if (image.id !in existingIds && image.localPath != null) {
                repository.addImage(transactionId, image.id, image.localPath)
            }
        }
    }
}
