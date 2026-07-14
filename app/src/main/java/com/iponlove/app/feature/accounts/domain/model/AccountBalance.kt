package com.iponlove.app.feature.accounts.domain.model

import java.math.BigDecimal

/**
 * An [account] paired with its ledger-derived current balance (ADR-0007). The list element of
 * [com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountBalancesUseCase] — used by the
 * balance-widget account breakdown (v1.6.5 Item 33) so each row shows a per-account figure whose
 * sum equals the "Net assets" header.
 */
data class AccountBalance(
    val account: Account,
    val balance: BigDecimal,
)
