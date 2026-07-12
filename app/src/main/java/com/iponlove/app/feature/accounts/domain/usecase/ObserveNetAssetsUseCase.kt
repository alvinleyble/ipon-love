package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.transactions.domain.usecase.ObserveBalanceLedgerUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Total current balance across the user's own/shared-by-me accounts (ADR-0011), active accounts
 * only — archiving/unarchiving never moves this figure. The single source Accounts and Analysis
 * (Item 14) both read, so the two screens always agree on "Net assets".
 */
class ObserveNetAssetsUseCase @Inject constructor(
    private val observeAccounts: ObserveAccountsUseCase,
    private val observeBalanceLedger: ObserveBalanceLedgerUseCase,
) {
    operator fun invoke(): Flow<BigDecimal> =
        combine(observeAccounts(includeArchived = true), observeBalanceLedger()) { accounts, ledger ->
            val openingBalances = accounts.associate { it.id to it.openingBalance }
            val balances = AccountBalanceCalculator.balances(openingBalances, ledger)
            accounts.filterNot { it.isArchived }
                .fold(BigDecimal.ZERO) { acc, a -> acc + (balances[a.id] ?: a.openingBalance) }
        }
}
