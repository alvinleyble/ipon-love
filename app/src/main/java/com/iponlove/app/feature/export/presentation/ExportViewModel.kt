package com.iponlove.app.feature.export.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.core.network.ConnectivityObserver
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.export.data.ExportFileWriter
import com.iponlove.app.feature.export.data.PdfExporter
import com.iponlove.app.feature.export.data.ZipExporter
import com.iponlove.app.feature.export.domain.CsvExporter
import com.iponlove.app.feature.export.domain.ExportFilename
import com.iponlove.app.feature.export.domain.ExportScopeLabel
import com.iponlove.app.feature.export.domain.ReceiptNumbering
import com.iponlove.app.feature.export.domain.model.ExportData
import com.iponlove.app.feature.export.domain.model.ExportFormat
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRanges
import com.iponlove.app.feature.export.domain.model.ExportRowMapper
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import com.iponlove.app.feature.transactions.presentation.FilterOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives the Records export sheet (v1.7.0 Item 6). Export is **fully self-contained**: its own
 * filter and date range, starting blank + month-to-date, decoupled from Records' own applied filter
 * and viewed month (the original design silently inherited both, which left no way to discover that
 * a subset could be exported at all).
 *
 * Accounts/categories (and everything derived from them — the "What to include" label, the filter
 * sheet's picker options) are deliberately observed **independently** of the date range: an invalid
 * range (From after To) must blank the *transaction count*, never make the applied filter look like
 * it silently reset.
 *
 * Slice 2 adds the attachment formats. Their work is genuinely long-running (one network fetch per
 * receipt), so it runs as a cancellable [exportJob] reporting numeric progress, and the partial file
 * is deleted on cancel or failure — an abandoned export must leave nothing behind for the startup
 * sweep to find.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val observeImages: ObserveTransactionImagesUseCase,
    observeCurrencySymbol: ObserveCurrencySymbolUseCase,
    connectivity: ConnectivityObserver,
    premiumGate: PremiumGate,
    private val fileWriter: ExportFileWriter,
    private val pdfExporter: PdfExporter,
    private val zipExporter: ZipExporter,
    private val analytics: Analytics,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(ZONE)

    private val filter = MutableStateFlow(TransactionFilter.NONE)
    private val fromDate = MutableStateFlow(YearMonth.from(today).atDay(1))
    private val toDate = MutableStateFlow(today)
    private val progress = MutableStateFlow<ExportProgress?>(null)

    private var exportJob: Job? = null

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
                        observeImages(),
                        entities,
                    ) { txns, images, names ->
                        if (names == null) return@combine null
                        val rows = f.apply(txns)
                            .sortedByDescending { it.date }
                            .map { txn ->
                                ExportRowMapper.toRow(
                                    transaction = txn,
                                    accountNames = names.accountNames,
                                    categoryNames = names.categoryNames,
                                    receipts = images[txn.id].orEmpty().map { image ->
                                        ExportPhoto(image.id, image.localPath, image.url)
                                    },
                                )
                            }
                        ExportData(rows, range)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val currency: StateFlow<CurrencySymbol> = observeCurrencySymbol()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CurrencySymbol.DEFAULT)

    /** Everything that only changes with the *scope*: label, counts, validity. */
    private val scopeState: StateFlow<ScopeState> =
        combine(exportData, entities, fromDate, toDate, filter) { data, names, from, to, f ->
            val invalid = from.isAfter(to)
            val numbered = data?.let { ReceiptNumbering.of(it.rows) }
            ScopeState(
                includeLabel = names?.let { ExportScopeLabel.of(f, it.categoryNames) } ?: "All transactions",
                transactionCount = data?.rows?.size ?: 0,
                photoCount = numbered?.photoCount ?: 0,
                downloadCount = numbered?.downloadCount ?: 0,
                from = from,
                to = to,
                invalid = invalid,
                filter = f,
                categories = names?.categories.orEmpty(),
                accounts = names?.accounts.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScopeState())

    val uiState: StateFlow<ExportUiState> =
        combine(
            scopeState,
            premiumGate.observeLocked(Scope.INDIVIDUAL),
            connectivity.observe(),
            progress,
        ) { scope, locked, online, running ->
            ExportUiState(
                includeLabel = scope.includeLabel,
                transactionCount = scope.transactionCount,
                photoCount = scope.photoCount,
                downloadCount = scope.downloadCount,
                fromDate = scope.from,
                toDate = scope.to,
                rangeInvalid = scope.invalid,
                ready = !scope.invalid && scope.transactionCount > 0,
                appliedFilter = scope.filter,
                filterableCategories = scope.categories,
                filterableAccounts = scope.accounts,
                attachmentsLocked = locked,
                offline = !online,
                progress = running,
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

    /**
     * Runs an export. CSV is instant and ungated; PDF/ZIP route to the paywall when locked, and
     * otherwise start the cancellable download-and-assemble job. No-op while one is already
     * running, or before the payload has resolved.
     */
    fun export(format: ExportFormat) {
        val state = uiState.value
        if (state.progress != null) return
        if (format.carriesAttachments && state.attachmentsLocked) {
            analytics.log("upsell_tap", source = UPSELL_SOURCE)
            events.trySend(ExportEvent.OpenPaywall(UPSELL_SOURCE))
            return
        }
        if (!state.enabled(format)) return
        val payload = exportData.value ?: return
        if (payload.rows.isEmpty()) return

        if (format == ExportFormat.CSV) {
            viewModelScope.launch {
                val uri = fileWriter.write(
                    ExportFilename.build(payload.range, format.extension),
                    CsvExporter.BOM + CsvExporter.build(payload.rows, ZONE),
                )
                events.send(ExportEvent.Share(uri, format.mimeType))
            }
            return
        }

        val numbered = ReceiptNumbering.of(payload.rows)
        val target = fileWriter.newFile(ExportFilename.build(payload.range, format.extension))
        progress.value = ExportProgress(format, done = 0, total = numbered.photoCount)
        exportJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.PDF -> pdfExporter.write(
                            target = target,
                            numbered = numbered,
                            range = payload.range,
                            currency = currency.value,
                            zone = ZONE,
                            onPhotoDone = { done -> progress.value = ExportProgress(format, done, numbered.photoCount) },
                        )
                        ExportFormat.ZIP -> zipExporter.write(
                            target = target,
                            numbered = numbered,
                            zone = ZONE,
                            onPhotoDone = { done -> progress.value = ExportProgress(format, done, numbered.photoCount) },
                        )
                        ExportFormat.CSV -> Unit // handled above
                    }
                }
                progress.value = null
                events.send(ExportEvent.Share(fileWriter.uriFor(target), format.mimeType))
            } catch (e: Throwable) {
                // Cancelled or failed: the half-written file is useless and must not linger
                // (Item 14's lesson). NonCancellable so the cleanup still runs on cancellation.
                withContext(NonCancellable) {
                    progress.value = null
                    runCatching { target.delete() }
                    if (e !is CancellationException) events.send(ExportEvent.Failed)
                }
                if (e is CancellationException) throw e
            } finally {
                exportJob = null
            }
        }
    }

    /** Cancel button, and sheet dismissal — an export is foreground-only (decision 5). */
    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope already cancels the job; the file cleanup rides the NonCancellable block.
        exportJob = null
    }

    private data class ScopeState(
        val includeLabel: String = "All transactions",
        val transactionCount: Int = 0,
        val photoCount: Int = 0,
        val downloadCount: Int = 0,
        val from: LocalDate = LocalDate.now(),
        val to: LocalDate = LocalDate.now(),
        val invalid: Boolean = false,
        val filter: TransactionFilter = TransactionFilter.NONE,
        val categories: List<FilterOption> = emptyList(),
        val accounts: List<FilterOption> = emptyList(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val UPSELL_SOURCE = "export"
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
