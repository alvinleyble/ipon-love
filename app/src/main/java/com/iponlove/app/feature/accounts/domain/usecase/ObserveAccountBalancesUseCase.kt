package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.model.AccountBalance
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.transactions.domain.usecase.ObserveBalanceLedgerUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Active accounts (own + shared-by-me, ADR-0011) in display order, each paired with its
 * ledger-derived current balance (ADR-0007) — the per-account companion to [ObserveNetAssetsUseCase]
 * (the balance widget's account breakdown, v1.6.5 Item 33). Because both derive balances from the
 * same opening-balances-plus-ledger pass, the rows always sum to the "Net assets" header. A shared
 * account shows its combined balance (both partners' postings, ADR-0018). Order follows the
 * repository (`position ASC`), matching Manage → Accounts.
 */
class ObserveAccountBalancesUseCase @Inject constructor(
    private val observeAccounts: ObserveAccountsUseCase,
    private val observeBalanceLedger: ObserveBalanceLedgerUseCase,
) {
    operator fun invoke(): Flow<List<AccountBalance>> =
        combine(observeAccounts(includeArchived = true), observeBalanceLedger()) { accounts, ledger ->
            val openingBalances = accounts.associate { it.id to it.openingBalance }
            val balances = AccountBalanceCalculator.balances(openingBalances, ledger)
            accounts.filterNot { it.isArchived }
                .map { AccountBalance(it, balances[it.id] ?: it.openingBalance) }
        }
}
