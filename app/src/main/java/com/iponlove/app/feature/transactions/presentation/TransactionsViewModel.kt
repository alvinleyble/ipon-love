package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.widget.presentation.Widgets
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.core.sync.SyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.BulkDeletePlan
import com.iponlove.app.feature.transactions.domain.usecase.BulkDeleteTransactionsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveHasAnyTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import com.iponlove.app.feature.widget.domain.usecase.CheckWidgetAdoptionUseCase
import com.iponlove.app.feature.widget.domain.usecase.WidgetNudgeVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeHasAnyTransaction: ObserveHasAnyTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val bulkDeleteTransactions: BulkDeleteTransactionsUseCase,
    private val syncEngine: SyncEngine,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
    private val checkWidgetAdoption: CheckWidgetAdoptionUseCase,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /** The calendar month currently paged to (ADR-0032); independent of Combined's own. */
    private val viewedMonth = MutableStateFlow(LocalDate.now(ZONE).withDayOfMonth(1))

    /** DEEP_HISTORY back-wall lock (individual scope, S10); false while dormant. Held as a
     *  StateFlow so [previousMonth] can read it synchronously as a backstop. */
    private val deepHistoryLocked: StateFlow<Boolean> =
        premiumGate.observeLocked(Scope.INDIVIDUAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsInRange: Flow<List<Transaction>> = viewedMonth.flatMapLatest { month ->
        val window = MonthWindow.windowFor(month, ZONE)
        observeTransactions(window.startInclusive, window.endExclusive)
    }

    /** The applied Records filter (v1.7.0 Item 7). Session-scoped, deliberately NOT persisted and
     *  independent of [viewedMonth] — the lens survives month-stepping and dies with the ViewModel. */
    private val filter = MutableStateFlow(TransactionFilter.NONE)

    /** Widget-adoption nudge visibility (Item 11) — kept as its own combine, mirroring Analysis'
     *  `showPairingCard`, so the main state combine below doesn't grow past Kotlin's typed 5-flow
     *  [combine] overload. The adoption check is a one-shot suspend call re-run on each fresh
     *  collection (Glance exposes no reactive adoption signal); [onWidgetNudgeCardShown] stamping
     *  "now" then makes this recompute false on the *next* subscription, not this one — the
     *  screen's own local latch (not this flow) is what keeps the card visible for the rest of
     *  the current visit. */
    private val showWidgetNudgeCard: Flow<Boolean> = combine(
        flow { emit(checkWidgetAdoption()) },
        onboardingRepository.observeWidgetNudgeLastShownAt(),
    ) { adopted, lastShownAt ->
        WidgetNudgeVisibility.shouldShow(adopted, lastShownAt, System.currentTimeMillis())
    }

    /** Records' multi-select (Item 7 / ADR-0064). Empty = not in selection mode. Session-scoped
     *  like [filter], and cleared by anything that changes which rows are on screen — see
     *  [clearSelection]'s callers — so a delete can never reach a ticked row the user can't see. */
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Non-null while the bulk-delete confirmation is up. Held here rather than in the composable
     *  because the counts it names come from a suspend read (`BulkDeleteTransactionsUseCase.plan`). */
    private val pendingBulkDelete = MutableStateFlow<BulkDeletePlan?>(null)

    /** Folded into the state through the single trailing [combine] below — paired first so the two
     *  selection flows cost one `.combine`, not two. */
    private val selection: Flow<Selection> =
        combine(selectedIds, pendingBulkDelete) { ids, plan -> Selection(ids, plan) }

    /**
     * The filter applied *upstream* of the main combine (which is already at its 5-flow ceiling):
     * grouping runs over already-filtered rows, and [FilteredTransactions] carries the applied
     * filter + the pre-filter row-presence flag the "No matches" empty state needs — so the main
     * combine's first slot stays a single flow.
     */
    private val filteredInRange: Flow<FilteredTransactions> =
        combine(transactionsInRange, filter) { txns, f ->
            FilteredTransactions(
                transactions = f.apply(txns),
                filter = f,
                hadRowsBeforeFilter = txns.isNotEmpty(),
            )
        }

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            filteredInRange,
            // Archived-inclusive so a historical row keeps its real account/category label after
            // that entity is archived (v1.6.7 Item 5): archiving = hide-from-picker only, it must
            // never degrade a past row to "Account"/"Uncategorized". The Add/Edit pickers live on
            // their own screen/ViewModel (archived-excluded), so this only affects display maps.
            observeAccounts(includeArchived = true),
            observeCategories(includeArchived = true),
            observeHasAnyTransaction(),
            viewedMonth,
        ) { filtered, accounts, categories, hasAnyEver, month ->
            val transactions = filtered.transactions
            val accountNames = accounts.associate { it.id to it.name }
            val categoryNames = categories.associate { it.id to it.name }
            // Icon/color keys for the Records row's tinted icon squircle (v1.6.7 Item 8 Slice 6a) —
            // only categories that set one contribute a map entry; toListItem's lookup is then a
            // plain miss (null) for icon-less categories, transfers, and settlements alike.
            val categoryIcons = categories.mapNotNull { c -> c.icon?.let { c.id to it } }.toMap()
            val categoryColors = categories.mapNotNull { c -> c.color?.let { c.id to it } }.toMap()
            val today = LocalDate.now(ZONE)
            val isCurrentMonth = YearMonth.from(month) == YearMonth.from(today)

            val dayGroups = DayGrouping.groupByDay(
                items = transactions.map {
                    it.toListItem(accountNames, categoryNames, categoryIcons, categoryColors)
                },
                dateOf = { it.date },
                zone = ZONE,
                today = today,
                isCurrentMonth = isCurrentMonth,
            )

            TransactionsUiState(
                isLoading = false,
                monthLabel = month.format(MONTH_FORMAT),
                dayGroups = dayGroups,
                // Select-all's whole reach (Item 7): the rendered rows, nothing else.
                visibleIds = dayGroups.flatMap { group -> group.items.map { it.id } },
                hasAnyTransactionEver = hasAnyEver,
                // `accounts` is now archived-inclusive (see the combine above), so gate Add on an
                // *active* account existing — the Add screen's account picker excludes archived.
                canAdd = accounts.any { !it.isArchived },
                canGoToNextMonth = MonthWindow.canStepForward(month, today),
                appliedFilter = filtered.filter,
                filterIsActive = filtered.filter.isActive,
                hadRowsBeforeFilter = filtered.hadRowsBeforeFilter,
                // Filter chips are active-only (decision 9): archiving hides an entity from the
                // picker even though a historical row keeps its real label above.
                filterableCategories = categories
                    .filter { !it.isArchived }
                    .map { FilterOption(it.id, it.name) },
                filterableAccounts = accounts
                    .filter { !it.isArchived }
                    .map { FilterOption(it.id, it.name) },
            )
        }.combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            // DEEP_HISTORY back-wall (S10). viewedMonth.value is read fresh — the same anchor that
            // produced `state`. Always allowed while dormant (locked = false).
            .combine(deepHistoryLocked) { state, locked ->
                state.copy(
                    canGoToPreviousMonth =
                        MonthWindow.canStepBack(viewedMonth.value, LocalDate.now(ZONE), locked),
                )
            }
            // Widget-adoption nudge card (Item 11) — same trailing-.combine escape hatch as above.
            .combine(showWidgetNudgeCard) { state, show ->
                state.copy(showWidgetNudgeCard = show)
            }
            // Multi-select (Item 7) — same escape hatch again.
            .combine(selection) { state, sel ->
                state.copy(
                    selectedIds = sel.selectedIds,
                    pendingBulkDelete = sel.pendingBulkDelete,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

    fun previousMonth() {
        // Backstop for the DEEP_HISTORY back-wall (the ← is also lock-affordanced in the UI).
        if (!MonthWindow.canStepBack(viewedMonth.value, LocalDate.now(ZONE), deepHistoryLocked.value)) return
        clearSelection()
        viewedMonth.value = MonthWindow.step(viewedMonth.value, forward = false)
    }

    /** A tap on the locked ← at the −12mo wall — logs the §10.10 touchpoint before the screen
     *  routes to the paywall; the month does not move. */
    fun onDeepHistoryUpsell(): String {
        val source = "deep_history"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun nextMonth() {
        val current = viewedMonth.value
        if (MonthWindow.canStepForward(current, LocalDate.now(ZONE))) {
            clearSelection()
            viewedMonth.value = MonthWindow.step(current, forward = true)
        }
    }

    fun sync() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (_: Exception) {
                // SyncEngine already surfaces the error via SyncState.Error;
                // swallow here so an uncaught exception doesn't crash the app.
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            deleteTransaction(id)
            Widgets.updateAll(context)
        }
    }

    /** Commits the sheet's draft filter (v1.7.0 Item 7). Session-scoped — reflows the list upstream
     *  of the main combine, survives month-stepping, and is never persisted. */
    fun applyFilter(filter: TransactionFilter) {
        clearSelection()
        this.filter.value = filter
    }

    /** Resets the Records filter to unfiltered — used by the sheet's Clear and the "No matches"
     *  empty-state inline action. */
    fun clearFilter() {
        clearSelection()
        filter.value = TransactionFilter.NONE
    }

    // ---- multi-select (v1.7.3 Item 7 / ADR-0064) ----

    /** Long-press on a row: enter selection mode with that row ticked. */
    fun startSelection(id: String) {
        selectedIds.value = RecordsSelection.begin(id)
    }

    /** Tap on a row while in selection mode. Unticking the last row exits — no separate flag. */
    fun toggleSelection(id: String) {
        selectedIds.value = RecordsSelection.toggle(selectedIds.value, id)
    }

    /** Select-all / clear-all over the currently rendered, post-filter rows only — never history
     *  outside the viewed month (ADR-0064 decision 6). */
    fun toggleSelectAll() {
        selectedIds.value = RecordsSelection.toggleAll(selectedIds.value, uiState.value.visibleIds)
    }

    /** The ✕, the system back gesture, and anything that changes which rows are on screen. */
    fun clearSelection() {
        selectedIds.value = emptySet()
        pendingBulkDelete.value = null
    }

    /** Opens the confirmation, which needs a read to know what the delete would really touch. */
    fun requestBulkDelete() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val plan = bulkDeleteTransactions.plan(ids)
            // Nothing live left behind the ticks (a sync retired them first): don't put up a
            // confirmation for a delete that would write nothing — just drop out of selection.
            if (plan.isEmpty) clearSelection() else pendingBulkDelete.value = plan
        }
    }

    fun dismissBulkDelete() {
        pendingBulkDelete.value = null
    }

    /** Deletes the whole selection in one atomic pass, then exits selection mode. */
    fun confirmBulkDelete() {
        val ids = selectedIds.value
        pendingBulkDelete.value = null
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bulkDeleteTransactions(ids)
            selectedIds.value = emptySet()
            Widgets.updateAll(context)
        }
    }

    /** Stamps the widget-adoption nudge card (Item 11) as shown *now* — called by the card's own
     *  composition, not a dismiss action, so simply appearing (whether or not the user taps ✕)
     *  starts the 30-day non-naggy cooldown. */
    fun onWidgetNudgeCardShown() {
        viewModelScope.launch { onboardingRepository.recordWidgetNudgeShown() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}

/**
 * Carries the filter result into the main combine's single first slot (v1.7.0 Item 7): the
 * post-filter rows plus the two facts the UiState needs but the filtered list alone can't supply —
 * the applied [filter] (for the active dot + sheet seeding) and [hadRowsBeforeFilter] (to tell the
 * "No matches" empty state from a genuinely empty month).
 */
private data class FilteredTransactions(
    val transactions: List<Transaction>,
    val filter: TransactionFilter,
    val hadRowsBeforeFilter: Boolean,
)

/** Pairs the two multi-select flows (Item 7) so they fold in through one trailing `.combine`. */
private data class Selection(
    val selectedIds: Set<String>,
    val pendingBulkDelete: BulkDeletePlan?,
)

/**
 * Maps a [Transaction] to its Records row. Extracted from the ViewModel so the display
 * logic (in particular the category-less label branches) is unit-testable without Hilt.
 * [categoryIcons]/[categoryColors] (v1.6.7 Item 8 Slice 6a) default to empty so existing callers
 * (and [TransactionsListItemMapperTest]) keep compiling unchanged — transfers/settlements never
 * carry a category, so they fall back to the row's letter-avatar treatment regardless.
 */
internal fun Transaction.toListItem(
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    categoryIcons: Map<String, String> = emptyMap(),
    categoryColors: Map<String, String> = emptyMap(),
): TransactionListItem {
    val accountName = accountNames[accountId] ?: "Account"
    val noteSuffix = note?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty()
    return when {
        type == TransactionType.TRANSFER -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = "Transfer",
            subtitle = "$accountName → ${accountNames[toAccountId] ?: "Account"}$noteSuffix",
            date = date,
        )
        // Debt settlement legs carry categoryId = null + isSettlement = true by design
        // (ADR-0019 #14 / ADR-0042). Label them off the flag instead of falling to
        // "Uncategorized", mirroring the TRANSFER branch above.
        isSettlement -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = "Debt settlement",
            subtitle = "$accountName$noteSuffix",
            date = date,
        )
        // Balance-correction rows (ADR-0057) show the label only — the auto "from → to" note is
        // still written to `note` (surfaces on opening the row / in a CSV) but is deliberately not
        // appended to the list subtitle, unlike every other categoryless branch here, so the list
        // stays uncluttered (Alvin's call, against the initial recommendation).
        isAdjustment -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = "Balance adjustment",
            subtitle = accountName,
            date = date,
        )
        else -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = categoryNames[categoryId] ?: "Uncategorized",
            subtitle = "$accountName$noteSuffix",
            date = date,
            categoryIcon = categoryId?.let { categoryIcons[it] },
            categoryColor = categoryId?.let { categoryColors[it] },
        )
    }
}
