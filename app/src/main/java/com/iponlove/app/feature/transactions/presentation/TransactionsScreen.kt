package com.iponlove.app.feature.transactions.presentation

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.recurring.presentation.components.ComingUpCard
import com.iponlove.app.feature.recurring.presentation.components.PendingConfirmationsCard
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.presentation.components.TransactionFilterSheet
import com.iponlove.app.feature.widget.presentation.BalanceWidgetReceiver

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
    onEditTransaction: (String) -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TransactionsContent(
        state = state,
        onSync = viewModel::sync,
        onAdd = onAddTransaction,
        onEdit = onEditTransaction,
        onDelete = viewModel::delete,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        // Locked ← at the DEEP_HISTORY −12mo wall: log the touchpoint, then route to the paywall.
        onDeepHistoryUpsell = { onOpenPremium(viewModel.onDeepHistoryUpsell()) },
        onOpenPremium = onOpenPremium,
        onApplyFilter = viewModel::applyFilter,
        onClearFilter = viewModel::clearFilter,
        onWidgetNudgeCardShown = viewModel::onWidgetNudgeCardShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDeepHistoryUpsell: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    onApplyFilter: (TransactionFilter) -> Unit = {},
    onClearFilter: () -> Unit = {},
    onWidgetNudgeCardShown: () -> Unit = {},
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
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(
                    title = "Records",
                    leadingActions = {
                        // The filter icon carries an accent dot whenever a filter is applied — the
                        // user's only explanation for why rows are hidden (Item 7 decision 1). Gated
                        // on hasAnyTransactionEver: nothing to filter on a fresh install, but it
                        // stays available in an empty *month* so a filter can be ruled out.
                        if (state.hasAnyTransactionEver) {
                            IconButton(onClick = { filterSheetOpen = true }) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = "Filter transactions",
                                        tint = colors.textSecondary,
                                    )
                                    if (state.filterIsActive) {
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
        },
        floatingActionButton = {
            if (state.canAdd) {
                RecordsFab(onClick = onAdd)
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
                                    onClick = { onEdit(item.id) },
                                    onDelete = { onDelete(item.id) },
                                )
                            }
                        }
                    }
                }
            }
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

/** The center −4° squircle FAB, matching the global add-transaction FAB's identity language
 *  (Savings' `SavingsFab` recipe). */
@Composable
private fun RecordsFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .rotate(-4f)
            .size(56.dp)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add transaction",
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
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

@Composable
private fun TransactionRow(
    item: TransactionListItem,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = LocalPlayfulColors.current
    val squircleColor = parseHexColor(item.categoryColor) ?: colors.accent
    val squircleInk = if (item.categoryColor != null) Color.White else colors.onAccent
    val imageVector = item.categoryIcon?.let { CATEGORY_ICONS[it] }

    PlayfulCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
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
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatShortDate(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = item.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = item.amountColor(),
            )
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
