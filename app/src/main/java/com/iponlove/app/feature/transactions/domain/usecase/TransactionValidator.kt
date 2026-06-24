package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal

/** A single thing wrong with a would-be transaction. */
enum class TransactionError {
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_REQUIRED,
    CATEGORY_REQUIRED,           // income/expense must be filed under a category
    DESTINATION_REQUIRED,        // a transfer needs a target account
    DESTINATION_SAME_AS_SOURCE,  // a transfer can't go to its own source
}

/**
 * Pure validation of transaction inputs, shared by the UI (inline errors) and the
 * use case (final gate). Type-specific fields the UI can't produce for a given type
 * (e.g. a category on a transfer) are normalised away before saving, not validated here.
 */
object TransactionValidator {

    fun validate(
        type: TransactionType,
        amount: BigDecimal,
        accountId: String?,
        toAccountId: String?,
        categoryId: String?,
    ): List<TransactionError> {
        val errors = mutableListOf<TransactionError>()

        if (amount.signum() <= 0) errors += TransactionError.AMOUNT_NOT_POSITIVE
        if (accountId.isNullOrBlank()) errors += TransactionError.ACCOUNT_REQUIRED

        when (type) {
            TransactionType.INCOME, TransactionType.EXPENSE ->
                if (categoryId.isNullOrBlank()) errors += TransactionError.CATEGORY_REQUIRED

            TransactionType.TRANSFER ->
                when {
                    toAccountId.isNullOrBlank() -> errors += TransactionError.DESTINATION_REQUIRED
                    toAccountId == accountId -> errors += TransactionError.DESTINATION_SAME_AS_SOURCE
                }
        }
        return errors
    }
}
