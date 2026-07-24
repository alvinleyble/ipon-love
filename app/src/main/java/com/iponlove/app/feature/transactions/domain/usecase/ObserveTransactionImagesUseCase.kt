package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * transactionId → ordered receipt images, **including rows still pending upload**. The export
 * facility's photo source (v1.7.0 Item 6 Slice 2): unlike
 * [ObserveTransactionImageUrlsUseCase] — which is display-oriented and can only hand back a URL —
 * this keeps `localPath` too, so a receipt that hasn't reached Storage yet still exports (from the
 * local file, with no network round trip).
 */
class ObserveTransactionImagesUseCase @Inject constructor(
    private val repository: TransactionImageRepository,
) {
    operator fun invoke(): Flow<Map<String, List<TransactionImage>>> = repository.observeImages()
}
