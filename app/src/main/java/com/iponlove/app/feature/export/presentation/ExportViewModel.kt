package com.iponlove.app.feature.export.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.export.data.ExportFileWriter
import com.iponlove.app.feature.export.domain.CsvExporter
import com.iponlove.app.feature.export.domain.ExportFilename
import com.iponlove.app.feature.export.domain.ExportScopeLabel
import com.iponlove.app.feature.export.domain.model.ExportData
import com.iponlove.app.feature.export.domain.model.ExportRanges
import com.iponlove.app.feature.export.domain.model.ExportRowMapper
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionImageUrlsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import com.iponlove.app.feature.transactions.presentation.FilterOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives the Records export sheet (v1.7.0 Item 6, re-grilled 2026-07-24 — Slice 1, CSV). Export is
 * now **fully self-contained**: its own filter and date range, starting blank + month-to-date,
 * decoupled from Records' own applied filter and viewed month (the original design silently
 * inherited both, which left no way to discover that a subset could be exported at all).
 *
 * Accounts/categories (and everything derived from them — the "What to include" label, the filter
 * sheet's picker options) are deliberately observed **independently** of the date range: an invalid
 * range (From after To) must blank the *transaction count*, never make the applied filter look like
 * it silently reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val observeImageUrls: ObserveTransactionImageUrlsUseCase,
    private val fileWriter: ExportFileWriter,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(ZONE)

    private val filter = MutableStateFlow(TransactionFilter.NONE)
    private val fromDate = MutableStateFlow(YearMonth.from(today).atDay(1))
    private val toDate = MutableStateFlow(today)

    private val events = Channel<ExportEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ExportEvent> = events.receiveAsFlow()

    private data class NamedEntities(
        val accountNames: Map<String, String>,
        val categoryNames: Map<String, String>,
        val categories: List<FilterOption>,
        val accounts: List<FilterOption>,
    )

    // Archived-inclusive so a historical row keeps its real label (mirrors Records); the picker
    // options themselves are filtered to active-only below.
    private val entities: StateFlow<NamedEntities?> =
        combine(
            observeAccounts(includeArchived = true),
            observeCategories(includeArchived = true),
        ) { accounts, categories ->
            NamedEntities(
                accountNames = accounts.associate { it.id to it.name },
                categoryNames = categories.associate { it.id to it.name },
                categories = categories.filter { !it.isArchived }.map { FilterOption(it.id, it.name) },
                accounts = accounts.filter { !it.isArchived }.map { FilterOption(it.id, it.name) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val exportData: StateFlow<ExportData?> =
        combine(fromDate, toDate, filter) { from, to, f -> Triple(from, to, f) }
            .flatMapLatest { (from, to, f) ->
                if (from.isAfter(to)) {
                    flowOf(null)
                } else {
                    val range = ExportRanges.of(from, to, ZONE)
                    combine(
                        observeTransactions(range.startInclusive, range.endExclusive),
                        observeImageUrls(),
                        entities,
                    ) { txns, imageUrls, names ->
                        if (names == null) return@combine null
                        val counts = imageUrls.mapValues { it.value.size }
                        val rows = f.apply(txns)
                            .sortedByDescending { it.date }
                            .map {
                                ExportRowMapper.toRow(
                                    transaction = it,
                                    accountNames = names.accountNames,
                                    categoryNames = names.categoryNames,
                                    receiptCount = counts[it.id] ?: 0,
                                )
                            }
                        ExportData(rows, range)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<ExportUiState> =
        combine(exportData, entities, fromDate, toDate, filter) { data, names, from, to, f ->
            val invalid = from.isAfter(to)
            val count = data?.rows?.size ?: 0
            ExportUiState(
                includeLabel = names?.let { ExportScopeLabel.of(f, it.categoryNames) } ?: "All transactions",
                transactionCount = count,
                fromDate = from,
                toDate = to,
                rangeInvalid = invalid,
                ready = !invalid && count > 0,
                appliedFilter = f,
                filterableCategories = names?.categories.orEmpty(),
                filterableAccounts = names?.accounts.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExportUiState())

    fun setFromDate(date: LocalDate) {
        fromDate.value = date
    }

    fun setToDate(date: LocalDate) {
        toDate.value = date
    }

    /** Commits the stacked filter sheet's draft (decision 3) — "What to include" scoping. */
    fun applyFilter(newFilter: TransactionFilter) {
        filter.value = newFilter
    }

    fun clearFilter() {
        filter.value = TransactionFilter.NONE
    }

    /** Writes the CSV to a temp file and emits a [ExportEvent.Share] for the caller to hand to the
     *  share sheet. No-op until the payload has resolved and isn't empty. */
    fun exportCsv() {
        val payload = exportData.value ?: return
        if (payload.rows.isEmpty()) return
        viewModelScope.launch {
            val csv = CsvExporter.build(payload.rows, ZONE)
            val filename = ExportFilename.build(payload.range, "csv")
            val uri = fileWriter.write(filename, csv)
            events.send(ExportEvent.Share(uri, "text/csv"))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
