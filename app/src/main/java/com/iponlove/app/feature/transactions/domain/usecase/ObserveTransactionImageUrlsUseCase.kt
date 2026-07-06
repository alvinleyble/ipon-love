package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * transactionId → ordered receipt image URLs, across both members (own + replicated partner),
 * for the combined view (ADR-0011). Partner rows arrive redacted/purged through the
 * partner_transaction_images view, so only currently-visible receipts appear here.
 */
class ObserveTransactionImageUrlsUseCase @Inject constructor(
    private val repository: TransactionImageRepository,
) {
    operator fun invoke(): Flow<Map<String, List<String>>> = repository.observeImageUrls()
}
