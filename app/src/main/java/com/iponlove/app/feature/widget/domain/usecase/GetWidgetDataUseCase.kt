package com.iponlove.app.feature.widget.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.widget.domain.model.WidgetData
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetWidgetDataUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val client: SupabaseClient,
) {
    suspend operator fun invoke(): WidgetData? {
        if (client.auth.currentUserOrNull() == null) return null

        val accounts = accountRepository.observeAccounts(includeArchived = false).first()
        val transactions = transactionRepository.observeTransactions().first()

        val openingBalances = accounts.associate { it.id to it.openingBalance }
        val balances = AccountBalanceCalculator.balances(openingBalances, transactions)
        val totalBalance = balances.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        val today = LocalDate.now()
        val todaySpend = transactions
            .filter { txn ->
                txn.type == TransactionType.EXPENSE &&
                    txn.date.atZone(ZoneId.systemDefault()).toLocalDate() == today
            }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        return WidgetData(totalBalance = totalBalance, todaySpend = todaySpend)
    }
}
