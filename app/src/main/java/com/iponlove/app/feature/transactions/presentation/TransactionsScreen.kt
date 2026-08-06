package com.iponlove.app.feature.transactions.presentation

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.MonthStepperRow
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulScreenTitle
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.PrivacyEyeAction
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.drafts.presentation.components.DraftsCard
import com.iponlove.app.feature.recurring.presentation.components.ComingUpCard
import com.iponlove.app.feature.recurring.presentation.components.PendingConfirmationsCard
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.BulkDeletePlan
import com.iponlove.app.feature.transactions.presentation.components.TransactionFilterSheet
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.widget.presentation.BalanceWidgetReceiver
import kotlinx.coroutines.launch

/**
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6a): a transparent-container [Scaffold] with a
 * [PlayfulScreenTitle] (matching the Analysis/Savings standalone-screen pattern, since Records —
 * like those — owns its own top-level Scaffold rather than being hosted) carrying the filter icon
 * as a leading action and the Item 16 privacy eye as a trailing action (v1.7.1 Item 17 — the ⋮
 * overflow's sole entry, "Recurring rules," was dropped once Recurring became its own top-level
 * module); a −4° accent squircle FAB replaces the plain M3 one (matching Savings' `SavingsFab`).
 * Rows are glass [PlayfulCard]s with alternating leaf shapes and a category-tinted icon squircle
 * (falling back to a letter avatar for transfers/settlements/uncategorized rows, same fallback
 * [com.iponlove.app.feature.accounts.presentation]'s `AccountCard` uses); day groups get a heart
 * date-header (the Combined/Slice-4 recipe). Amounts use the derived `PlayfulColors.semantic`
 * ramp, replacing the old hardcoded `IncomeColor`/`colorScheme.error`.
 */
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onScanReceipt: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onOpenDrafts: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StartTourOnFirstVisit(TutorialTours.RECORDS_FAB_WHEEL)
    TransactionsContent(
        state = state,
        onSync = viewModel::sync,
        onAdd = onAddTransaction,
        onScanReceipt = onScanReceipt,
        onEdit = onEditTransaction,
        onOpenDrafts = onOpenDrafts,
        onDelete = viewModel::delete,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        // Locked ← at the DEEP_HISTORY −12mo wall: log the touchpoint, then route to the paywall.
        onDeepHistoryUpsell = { onOpenPremium(viewModel.onDeepHistoryUpsell()) },
        onOpenPremium = onOpenPremium,
        onApplyFilter = viewModel::applyFilter,
        onClearFilter = viewModel::clearFilter,
        onWidgetNudgeCardShown = viewModel::onWidgetNudgeCardShown,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onClearSelection = viewModel::clearSelection,
        onRequestBulkDelete = viewModel::requestBulkDelete,
        onDismissBulkDelete = viewModel::dismissBulkDelete,
        onConfirmBulkDelete = viewModel::confirmBulkDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onScanReceipt: () -> Unit = {},
    onEdit: (String) -> Unit,
    onOpenDrafts: () -> Unit = {},
    onDelete: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDeepHistoryUpsell: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    onApplyFilter: (TransactionFilter) -> Unit = {},
    onClearFilter: () -> Unit = {},
    onWidgetNudgeCardShown: () -> Unit = {},
    onStartSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRequestBulkDelete: () -> Unit = {},
    onDismissBulkDelete: () -> Unit = {},
    onConfirmBulkDelete: () -> Unit = {},
) {
    val colors = LocalPlayfulColors.current
    val context = LocalContext.current
    var filterSheetOpen by remember { mutableStateOf(false) }
    // Latched locally so the card doesn't vanish mid-visit the instant onWidgetNudgeCardShown's
    // DataStore write flows back through state.showWidgetNudgeCard as false (Item 11) — appearing
    // is what starts the 30-day cooldown, not tapping ✕, so the card must outlive that write.
    var widgetNudgeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.showWidgetNudgeCard) {
        if (state.showWidgetNudgeCard) {
            widgetNudgeVisible = true
            onWidgetNudgeCardShown()
        }
    }
    // Back exits selection mode before it can leave Records (Item 7) — same escape as the ✕.
    BackHandler(enabled = state.selectionMode, onBack = onClearSelection)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                // The contextual bar replaces the title row outright rather than trying to
                // coexist with the filter/bell/eye actions v1.7.1 Item 17 tidied (Item 7).
                if (state.selectionMode) {
                    SelectionTopBar(
                        selectedCount = state.selectedIds.size,
                        allVisibleSelected = state.allVisibleSelected,
                        onClose = onClearSelection,
                        onToggleSelectAll = onToggleSelectAll,
                        onDelete = onRequestBulkDelete,
                    )
                } else {
                    RecordsTopBar(
                        showFilterAction = state.hasAnyTransactionEver,
                        filterIsActive = state.filterIsActive,
                        onOpenFilter = { filterSheetOpen = true },
                    )
                }
            }
        },
        floatingActionButton = {
            // Adding a row mid-selection makes no sense — the bar's 🗑 is the only action here.
            if (state.canAdd && !state.selectionMode) {
                RecordsFabWheel(
                    // Two actions today. A third (🎤 voice) is expected once Item 3 un-defers
                    // (Horizon #3) — the wheel is generic over this list for exactly that reason
                    // (ADR-0062 decision 3), and a temporary placeholder action stood here during
                    // this slice's build to exercise 3-action cycling/direction/peek-stacking
                    // before Item 3 is real; removed once the captain confirmed the wheel felt
                    // right with three (see v1.7.3.md Item 2 Slice 3 for what was validated).
                    actions = listOf(
                        RecordsFabAction(Icons.Filled.Add, "Add transaction", onAdd),
                        RecordsFabAction(Icons.Filled.PhotoCamera, "Scan receipt", onScanReceipt),
                    ),
                    modifier = Modifier.coachMarkTarget(TutorialTargets.RECORDS_FAB),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.canAdd) {
                MonthStepperRow(
                    label = state.monthLabel,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    canGoNext = state.canGoToNextMonth,
                    canGoPrevious = state.canGoToPreviousMonth,
                    onPreviousLocked = onDeepHistoryUpsell,
                )
            }
            // Confirm-on-arrival "To confirm" card (Item 37) — pinned above the month-scoped list
            // (pending is relative to today, not the viewed month) and self-hides when empty.
            PendingConfirmationsCard(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // The parking area's entry point AND its only reminder (ADR-0066 decision 10) — same
            // pinned, self-hiding shape, and pinned for the same reason: a parked draft belongs to
            // no month, so it must not sit inside the month-scoped list below.
            DraftsCard(
                onOpen = onOpenDrafts,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // "Coming up" forecast preview (Item 37 Slice 2, premium) — next scheduled income +
            // bills; self-hides when empty, degrades to an upsell teaser when RECURRING_FORECAST
            // is enforced without access. Pinned below "To confirm", above the ledger list.
            ComingUpCard(
                onOpenPremium = onOpenPremium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // Widget-adoption nudge (Item 11) — non-adopters only, once per ~30-day cooldown.
            if (widgetNudgeVisible) {
                WidgetNudgeCard(
                    onOpen = {
                        val manager = AppWidgetManager.getInstance(context)
                        val provider = ComponentName(context, BalanceWidgetReceiver::class.java)
                        if (manager.isRequestPinAppWidgetSupported) {
                            manager.requestPinAppWidget(provider, null, null)
                        } else {
                            Toast.makeText(
                                context,
                                "Long-press your home screen, then choose Widgets to add Love, Ipon.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onDismiss = { widgetNudgeVisible = false },
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onSync,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.isLoading ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    !state.canAdd ->
                        EmptyState(
                            title = "Create an account first",
                            body = "Transactions need an account. Add one on the Accounts tab.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    state.dayGroups.isEmpty() && !state.hasAnyTransactionEver ->
                        EmptyState(
                            title = "No transactions yet",
                            body = "Tap + to record income, an expense, or a transfer.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    // Rows exist this month but the filter hid them all (decision 8) — distinct
                    // from a genuinely empty month, with an inline escape so the user needn't
                    // reopen the sheet.
                    state.dayGroups.isEmpty() && state.filterIsActive && state.hadRowsBeforeFilter ->
                        EmptyState(
                            title = "No matches",
                            body = "No transactions this month match your filters.",
                            action = "Clear filters" to onClearFilter,
                            modifier = Modifier.align(Alignment.Center),
                        )

                    state.dayGroups.isEmpty() ->
                        EmptyState(
                            title = "No transactions this month",
                            body = "Nothing recorded yet for ${state.monthLabel}.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.dayGroups.forEach { group ->
                            stickyHeader(key = group.label) { DayHeader(group.label) }
                            itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                                TransactionRow(
                                    item = item,
                                    index = index,
                                    selectionMode = state.selectionMode,
                                    isSelected = item.id in state.selectedIds,
                                    // Tap means edit outside selection mode, tick inside it.
                                    onClick = {
                                        if (state.selectionMode) onToggleSelection(item.id)
                                        else onEdit(item.id)
                                    },
                                    onLongClick = { onStartSelection(item.id) },
                                    onDelete = { onDelete(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        state.pendingBulkDelete?.let { plan ->
            BulkDeleteConfirmDialog(
                plan = plan,
                onConfirm = onConfirmBulkDelete,
                onDismiss = onDismissBulkDelete,
            )
        }

        if (filterSheetOpen) {
            TransactionFilterSheet(
                applied = state.appliedFilter,
                categories = state.filterableCategories,
                accounts = state.filterableAccounts,
                onApply = {
                    onApplyFilter(it)
                    filterSheetOpen = false
                },
                onClear = {
                    onClearFilter()
                    filterSheetOpen = false
                },
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

/** Records' ordinary title row. Extracted when multi-select gave it a sibling (Item 7), so the
 *  two top bars read as the alternatives they are rather than one nested inside the other. */
@Composable
private fun RecordsTopBar(
    showFilterAction: Boolean,
    filterIsActive: Boolean,
    onOpenFilter: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    PlayfulScreenTitle(
        title = "Records",
        leadingActions = {
            // The filter icon carries an accent dot whenever a filter is applied — the user's only
            // explanation for why rows are hidden (v1.7.0 Item 7 decision 1). Gated on
            // hasAnyTransactionEver: nothing to filter on a fresh install, but it stays available
            // in an empty *month* so a filter can be ruled out.
            if (showFilterAction) {
                IconButton(onClick = onOpenFilter) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filter transactions",
                            tint = colors.textSecondary,
                        )
                        if (filterIsActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(LeafShapes.IconSquircle)
                                    .background(colors.accent),
                            )
                        }
                    }
                }
            }
        },
        actions = { PrivacyEyeAction() },
    )
}

/**
 * The contextual bar Records wears while rows are ticked (v1.7.3 Item 7): `✕  N selected … ☑ 🗑`.
 * It stands in for [PlayfulScreenTitle] rather than sharing the row with the filter/bell/eye
 * actions, so the only things reachable mid-selection are the three that act on the selection.
 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Exit selection", tint = colors.textPrimary)
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSelectAll) {
            Icon(
                imageVector = if (allVisibleSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                // Select-all only ever reaches this month's post-filter rows (ADR-0064 decision 6),
                // and the label says so — "all" here must not read as "all history".
                contentDescription = if (allVisibleSelected) "Clear selection" else "Select all shown",
                tint = colors.textPrimary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = colors.accent)
        }
    }
}

/**
 * The one thing standing between a mis-tapped selection and the loss (Item 7 Q5 — there is
 * deliberately no undo). It names the real row count, which can exceed what was ticked when a
 * transfer drags its linked fee in (ADR-0031), and warns about settlement rows, whose deletion now
 * puts the debt back to outstanding on both partners' boards (ADR-0065).
 */
@Composable
private fun BulkDeleteConfirmDialog(
    plan: BulkDeletePlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan.rowCount == 1) "Delete 1 record?" else "Delete ${plan.rowCount} records?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.untickedFeeCount > 0) {
                    Text(
                        text = if (plan.untickedFeeCount == 1) {
                            "That includes 1 linked transfer fee you didn't select."
                        } else {
                            "That includes ${plan.untickedFeeCount} linked transfer fees you didn't select."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.settlementCount > 0) {
                    Text(
                        "Any debts you've settled with your partner will show as outstanding " +
                            "again on the Partner Debt Tracker, for both of you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One action the Records FAB wheel can be armed with (ADR-0062 decision 3). */
private data class RecordsFabAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

private val WheelArmedSize = 56.dp
private val WheelPeekSize = 40.dp
private val WheelArmedIconSize = 27.dp
private val WheelPeekIconSize = 19.dp
private val WheelSlotGap = 10.dp
private val WheelSwipeThreshold = 32.dp
private const val WheelPeekAlpha = 0.25f

/**
 * The Records door: one FAB position, generic over an action list. **The armed action stays in
 * the visual middle**, with the next action peeking above and the previous one peeking below
 * (revised live, 2026-08-06 — the original design peeked everything above a bottom-anchored armed
 * slot). Directly tappable without swiping first — the swipe is polish, not a toll gate (ADR-0062
 * decision 3). **Always resets to the first action** on a fresh composition: `armedIndex` is a
 * plain (non-saveable) `remember`, deliberately not `rememberSaveable`/DataStore, per the ADR's
 * "no stored preference" rule — leaving Records and coming back always re-lands on `＋`.
 *
 * The swap is **visually live**, not just decided at drag-end: [dragPx] tracks the raw finger
 * offset for the duration of one drag, and every slot's size/opacity is a direct function of it —
 * the armed slot visibly shrinks and dims while the target peek grows and brightens, in lockstep
 * with the finger, exactly like a real interactive wheel. Only the *decision* (does this drag
 * count as a step) is still made once, at drag-end, past `WheelSwipeThreshold` — see
 * [RecordsFabArmedSlot]'s doc for why that part stays a single discrete commit rather than a
 * distance-proportional stepper.
 */
@Composable
private fun RecordsFabWheel(actions: List<RecordsFabAction>, modifier: Modifier = Modifier) {
    var armedIndex by remember { mutableIntStateOf(0) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { WheelSwipeThreshold.toPx() }
    val n = actions.size
    val nextIndex = (armedIndex + 1).mod(n)
    val prevIndex = (armedIndex - 1).mod(n)
    // Swipe UP pulls the item BELOW up into focus (standard scroll/carousel feel — content follows
    // the finger), so the below neighbor renders above, and vice versa. Inverted live 2026-08-06
    // after watching the un-inverted mapping (next renders above, arms on swipe-up) feel backwards
    // once a 3rd action made the two directions visually distinct — with only 2 actions this is a
    // no-op, since prevIndex == nextIndex and the lone neighbor is shown above either way.
    val aboveIndex = prevIndex
    val belowIndex = nextIndex
    // -1 (fully armed the ABOVE slot) .. 0 (at rest) .. +1 (fully armed the BELOW slot).
    val progress = (dragPx / thresholdPx).coerceIn(-1f, 1f)

    fun settle(committed: Boolean, swipedUp: Boolean) {
        if (committed) {
            armedIndex = if (swipedUp) aboveIndex else belowIndex
            dragPx = 0f
        } else {
            // Didn't cross the threshold — spring the drag back to rest rather than snapping,
            // so the "it gave up and bounced back" case reads as deliberate, not glitchy.
            val start = dragPx
            scope.launch {
                Animatable(start).animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) {
                    dragPx = value
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (n > 1) {
            // Grows toward armed size/opacity as the user swipes UP (progress → -1).
            RecordsFabWheelSlot(action = actions[aboveIndex], growth = (-progress).coerceIn(0f, 1f))
            Spacer(Modifier.height(WheelSlotGap))
        }
        RecordsFabArmedSlot(
            action = actions[armedIndex],
            shrink = kotlin.math.abs(progress),
            onDrag = { delta -> dragPx += delta },
            onDragEnd = {
                val crossed = kotlin.math.abs(dragPx) >= thresholdPx
                settle(committed = crossed, swipedUp = dragPx < 0)
            },
            onDragCancel = { settle(committed = false, swipedUp = false) },
        )
        if (n > 2) {
            Spacer(Modifier.height(WheelSlotGap))
            // Grows toward armed size/opacity as the user swipes DOWN (progress → +1).
            RecordsFabWheelSlot(action = actions[belowIndex], growth = progress.coerceIn(0f, 1f))
        }
    }
}

/** The armed slot — full-size squircle FAB at rest, matching the app-wide add-transaction FAB
 *  identity language (Savings' `SavingsFab` recipe). Reports every raw drag pixel to [onDrag] so
 *  the parent can render the swap live, but the *decision* — does this drag count as a step — is
 *  still made exactly once, at [onDragEnd], however far past the threshold the finger travelled: a
 *  real swipe easily covers 200-400dp, and firing a step per threshold-width crossed mid-drag
 *  (the original bug) cycles the armed slot several times per gesture. A plain tap fires the armed
 *  action; [shrink] (0 = full size, 1 = shrunk to peek size) drives the live visual only.
 *
 *  `pointerInput(Unit)` below launches its gesture-detection coroutine once and — since its key
 *  never changes — never restarts it across recompositions, so a plain closure over [onDragEnd]
 *  would keep calling the *first* composition's lambda forever: it would only ever "see" the
 *  `armedIndex` that was current the first time this slot composed, so every swipe after the very
 *  first would decide the swap from stale, no-longer-current neighbor indices — a real swipe would
 *  work once and then appear to do nothing on subsequent tries. [rememberUpdatedState] is the
 *  standard fix: it lets the long-lived coroutine always read the *latest* callback. */
@Composable
private fun RecordsFabArmedSlot(
    action: RecordsFabAction,
    shrink: Float,
    onDrag: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    val size = lerp(WheelArmedSize, WheelPeekSize, shrink)
    val iconSize = lerp(WheelArmedIconSize, WheelPeekIconSize, shrink)
    val alpha = androidx.compose.ui.util.lerp(1f, WheelPeekAlpha, shrink)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .alpha(alpha)
            .rotate(-4f)
            .size(size)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                ) { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount)
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(iconSize).rotate(4f),
        )
    }
}

/** A peeking wheel action above or below the armed slot — directly tappable, firing its action
 *  immediately rather than merely arming it (ADR-0062 decision 3's discoverability rescue: a user
 *  who never realises the wheel scrolls can still reach it in one tap). At rest it sits small and
 *  dimmed to 25% opacity; [growth] (0 = full peek, 1 = full armed size/opacity) drives it growing
 *  and brightening in step with a live drag toward it. */
@Composable
private fun RecordsFabWheelSlot(action: RecordsFabAction, growth: Float, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    val size = lerp(WheelPeekSize, WheelArmedSize, growth)
    val iconSize = lerp(WheelPeekIconSize, WheelArmedIconSize, growth)
    val alpha = androidx.compose.ui.util.lerp(WheelPeekAlpha, 1f, growth)
    Box(
        modifier = modifier
            .alpha(alpha)
            .rotate(-4f)
            .size(size)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(iconSize).rotate(4f),
        )
    }
}

/** Occasional discovery tip toward the home-screen widgets (Item 11) — same card shape as
 *  Analysis' `PairingNudgeCard`. In-app only, no OS push, no notification-inbox row. */
@Composable
private fun WidgetNudgeCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        surface = PlayfulSurface.Blush,
        shape = LeafShapes.Card,
        contentPadding = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    "Try the Love, Ipon widget",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBlush,
                )
                Text(
                    "Check your balance or log an expense right from your home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBlushSecondary,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = colors.onBlush,
                )
            }
        }
    }
}

/** A heart date-header (matching Combined's `DayHeader`, Slice 4) — opaque-filled so the sticky
 *  header cleanly covers rows scrolling underneath it. */
@Composable
private fun DayHeader(label: String) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundBottom)
            .background(colors.glass)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeartBullet(colors.accent, sizeDp = 12)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    item: TransactionListItem,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = LocalPlayfulColors.current
    // A ticked row lifts onto the Blush surface and swaps its category squircle for an accent
    // check — the tick shares the icon's slot, so the row keeps its height while the state reads
    // at a glance. Inks follow the surface so the swap stays legible in every palette.
    val surface = if (isSelected) PlayfulSurface.Blush else PlayfulSurface.Glass
    val primaryInk = onPlayfulSurface(surface)
    val secondaryInk = if (isSelected) colors.onBlushSecondary else colors.textSecondary
    val tertiaryInk = if (isSelected) colors.onBlushSecondary else colors.textTertiary
    val squircleColor = if (isSelected) colors.accent else parseHexColor(item.categoryColor) ?: colors.accent
    val squircleInk = if (!isSelected && item.categoryColor != null) Color.White else colors.onAccent
    val imageVector = if (isSelected) Icons.Filled.Check else item.categoryIcon?.let { CATEGORY_ICONS[it] }

    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        surface = surface,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(LeafShapes.IconSquircle).background(squircleColor),
                contentAlignment = Alignment.Center,
            ) {
                if (imageVector != null) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = squircleInk,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = item.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = squircleInk,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatShortDate(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiaryInk,
                )
            }
            Text(
                text = item.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = item.amountColor(),
            )
            // The per-row kebab stands down in selection mode: a single-row delete sitting next to
            // a ticked selection is exactly the ambiguity the contextual bar exists to remove.
            if (!selectionMode) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = colors.textSecondary,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = action.second) { Text(action.first) }
        }
    }
}

@Composable
private fun TransactionListItem.signedAmount(): String {
    val prefix = when (type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "−"
        TransactionType.TRANSFER -> ""
    }
    return prefix + money(amount)
}

@Composable
private fun TransactionListItem.amountColor(): Color {
    val colors = LocalPlayfulColors.current
    return when (type) {
        TransactionType.INCOME -> colors.semantic.income
        TransactionType.EXPENSE -> colors.semantic.negative
        TransactionType.TRANSFER -> colors.textSecondary
    }
}
