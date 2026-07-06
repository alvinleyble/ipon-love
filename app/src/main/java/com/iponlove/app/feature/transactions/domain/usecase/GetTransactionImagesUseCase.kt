package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import javax.inject.Inject

/** One-shot load of a transaction's active receipt images, for seeding the editor when editing. */
class GetTransactionImagesUseCase @Inject constructor(
    private val repository: TransactionImageRepository,
) {
    suspend operator fun invoke(transactionId: String): List<TransactionImage> =
        repository.getImages(transactionId)
}
