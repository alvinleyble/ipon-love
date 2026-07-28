package com.iponlove.app.feature.analysis.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulScreenTitle
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.PrivacyEyeAction
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.presentation.components.DailyNetCalendarChart
import com.iponlove.app.feature.analysis.presentation.components.DonutChart
import com.iponlove.app.feature.analysis.presentation.components.DonutSlice
import com.iponlove.app.feature.analysis.presentation.components.ExpenseFlowChart
import com.iponlove.app.feature.analysis.presentation.components.sliceColor
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun AnalysisScreen(
    onOpenCouple: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AnalysisContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onOpenCouple = onOpenCouple,
        onDismissPairingCard = viewModel::dismissPairingCard,
        // Locked extended-range tap: log the funnel touchpoint, then route to the paywall.
        onExtendedRangeUpsell = { onOpenPremium(viewModel.onExtendedRangeUpsell()) },
        // Locked ← at the DEEP_HISTORY −12mo wall: same treatment, its own analytics source.
        onDeepHistoryUpsell = { onOpenPremium(viewModel.onDeepHistoryUpsell()) },
        // Locked month-end forecast teaser (Item 37 Slice 2): same treatment, its own source.
        onForecastUpsell = { onOpenPremium(viewModel.onForecastUpsell()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisContent(
    state: AnalysisUiState,
    onSelectPeriod: (AnalysisPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCouple: () -> Unit,
    onDismissPairingCard: () -> Unit,
    onExtendedRangeUpsell: () -> Unit = {},
    onDeepHistoryUpsell: () -> Unit = {},
    onForecastUpsell: () -> Unit = {},
) {
    StartTourOnFirstVisit(TutorialTours.ANALYSIS)
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Calendar ⟺ 1M coupling (Item 3B): the Calendar day-grid is inherently monthly, so it only
    // ever runs under 1M. Landing on Calendar under any other range auto-snaps to 1M (covers both
    // a tab tap and a swipe); tapping a non-1M range while on Calendar bounces back to Donut.
    LaunchedEffect(pagerState.currentPage, state.period) {
        if (pagerState.currentPage == CALENDAR_TAB && state.period != AnalysisPeriod.MONTH) {
            onSelectPeriod(AnalysisPeriod.MONTH)
        }
    }
    val onSelectPeriodCoupled: (AnalysisPeriod) -> Unit = { period ->
        // ANALYSIS_EXTENDED_RANGES soft-gate (S10): a locked 3M/6M/12M/ALL tap routes to the
        // paywall and does NOT switch the range; free ranges (1D/1W/1M) fall through unchanged.
        if (period.isExtendedRange && state.extendedRangesLocked) {
            onExtendedRangeUpsell()
        } else {
            if (pagerState.currentPage == CALENDAR_TAB && period != AnalysisPeriod.MONTH) {
                scope.launch { pagerState.animateScrollToPage(DONUT_TAB) }
            }
            onSelectPeriod(period)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(
                    title = "Analysis",
                    leadingActions = {
                        val colors = LocalPlayfulColors.current
                        // Net-assets figure retained (Item 14 parity) — recreated in the Playful
                        // style per the pure-reskin hard rule; masks under the global privacy eye.
                        // Sits left of the bell/eye (Item 16 reorder), via PlayfulScreenTitle's
                        // leadingActions slot.
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                            Text(
                                text = "Net assets",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = money(state.netAssets),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                        }
                    },
                    actions = { PrivacyEyeAction() },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Persistent header — always visible regardless of period or tab.
            PeriodSelector(
                selected = state.period,
                onSelect = onSelectPeriodCoupled,
                lockedExtended = state.extendedRangesLocked,
            )
            PeriodStepper(
                label = state.periodLabel,
                onPrevious = onPrevious,
                onNext = onNext,
                // Forward is capped at the current period for the free 1D/1W/1M ranges (Item 3B);
                // ALL_TIME has nothing to step to in either direction. Backward is unlimited except
                // at the DEEP_HISTORY −12mo wall (locked, free ranges) where the ← becomes a lock.
                canPrevious = state.period != AnalysisPeriod.ALL_TIME,
                canNext = state.canStepForward,
                previousLocked = !state.canStepBackward,
                onPreviousLocked = onDeepHistoryUpsell,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // The one activation-event entry point for unpaired users now that pairing has
            // moved out of the main nav into Settings → Couple (ADR-0024).
            if (state.showPairingCard) {
                PairingNudgeCard(onOpen = onOpenCouple, onDismiss = onDismissPairingCard)
            }

            SummaryCard(
                label = state.periodLabel,
                income = state.totalIncome,
                expense = state.totalExpense,
                net = state.net,
                lastMonthIncome = state.lastMonthIncome,
                projectedNet = state.projectedNet,
                showForecastUpsell = state.showForecastUpsell,
                onForecastUpsell = onForecastUpsell,
            )

            AnalysisTabLayout(
                state = state,
                pagerState = pagerState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─── Tab layout (all ranges) ─────────────────────────────────────────────────

private const val DONUT_TAB = 0
private const val FLOW_TAB = 1
private const val CALENDAR_TAB = 2

@Composable
private fun AnalysisTabLayout(
    state: AnalysisUiState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabLabels = listOf("Donut", "Flow", "Calendar")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 4.dp)
                .coachMarkTarget(TutorialTargets.ANALYSIS_TABS),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabLabels.forEachIndexed { index, label ->
                PlayfulChip(
                    label = label,
                    selected = pagerState.currentPage == index,
                    // Tapping Calendar snaps to it; the LaunchedEffect above forces 1M.
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            when (page) {
                DONUT_TAB -> DonutTab(state)
                FLOW_TAB -> FlowTab(state)
                CALENDAR_TAB -> CalendarTab(state)
                else -> {}
            }
        }
    }
}

@Composable
private fun DonutTab(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.hasExpenses) BreakdownSection(state) else EmptyState()
    }
}

@Composable
private fun FlowTab(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.expenseFlow?.let { flow ->
            // A 1-bucket range (1D) can't draw a curve — show a short-range note instead.
            if (flow.isChartable) ExpenseFlowSection(flow) else ShortRangeFlowCard()
        }
        state.flowMetrics?.let { metrics -> FlowMetricsSection(metrics) }
    }
}

@Composable
private fun CalendarTab(state: AnalysisUiState) {
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.calendarNet?.let { cal ->
            CalendarInsightsCard(
                biggestSpendDay = state.calendarBiggestSpendDay,
                noSpendDayCount = state.calendarNoSpendDayCount,
            )
            CalendarNetSection(
                calendarNet = cal,
                selectedDay = selectedDay,
                onDayClick = { day -> selectedDay = if (selectedDay == day) null else day },
            )
            selectedDay?.let { day ->
                val dayUi = cal.days.find { it.dayOfMonth == day }
                if (dayUi != null) DayDetailCard(day = day, dayUi = dayUi)
            }
        }
    }
}

// ─── Persistent header composables ──────────────────────────────────────────

@Composable
private fun PeriodSelector(
    selected: AnalysisPeriod,
    onSelect: (AnalysisPeriod) -> Unit,
    lockedExtended: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .coachMarkTarget(TutorialTargets.ANALYSIS_PERIOD),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnalysisPeriod.entries.forEach { period ->
            val locked = lockedExtended && period.isExtendedRange
            PlayfulChip(
                label = period.shortLabel(),
                selected = selected == period,
                onClick = { onSelect(period) },
                bigCorner = 14.dp,
                smallCorner = 5.dp,
                trailing = if (locked) {
                    {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Premium",
                            modifier = Modifier.size(12.dp),
                            tint = LocalPlayfulColors.current.textSecondary,
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun PeriodStepper(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    previousLocked: Boolean = false,
    onPreviousLocked: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // At the DEEP_HISTORY back-wall the ← becomes a lock → paywall (§10.3), taking precedence
        // over the plain disabled state (which only applies to ALL_TIME). Free ranges only.
        val colors = LocalPlayfulColors.current
        if (previousLocked) {
            IconButton(onClick = onPreviousLocked) {
                Icon(Icons.Filled.Lock, contentDescription = "Unlock older history", tint = colors.textSecondary)
            }
        } else {
            IconButton(onClick = onPrevious, enabled = canPrevious) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous period",
                    tint = colors.textSecondary.copy(alpha = if (canPrevious) 0.55f else 0.2f),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Next period",
                tint = colors.textSecondary.copy(alpha = if (canNext) 0.55f else 0.2f),
            )
        }
    }
}

@Composable
private fun PairingNudgeCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
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
                    "Track money together",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBlush,
                )
                Text(
                    "Pair with your partner for a combined view, shared budgets & IOUs.",
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

@Composable
private fun SummaryCard(
    label: String,
    income: BigDecimal,
    expense: BigDecimal,
    net: BigDecimal,
    lastMonthIncome: BigDecimal? = null,
    projectedNet: BigDecimal? = null,
    showForecastUpsell: Boolean = false,
    onForecastUpsell: () -> Unit = {},
) {
    val colors = LocalPlayfulColors.current
    val netPositive = net.signum() >= 0
    Column(Modifier.fillMaxWidth()) {
        // Blush hero — "Spent" is the headline figure (design 1e); Income + Net ride below.
        PlayfulCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            surface = PlayfulSurface.Blush,
            shape = LeafShapes.Hero,
            tiltDegrees = -0.6f,
            contentPadding = 18.dp,
        ) {
            Column {
                Text(
                    text = "Spent · $label",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBlushSecondary,
                )
                Text(
                    text = money(expense),
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        color = colors.onBlush,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Income ${money(income)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBlushSecondary,
                    )
                    Text(
                        text = "Net " + (if (netPositive) "+" else "") + money(net),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (netPositive) colors.semantic.positiveOnBlush else colors.semantic.negativeOnBlush,
                    )
                }
            }
        }
        // Forecast / last-month footer (unchanged behavior) — kept below the hero, restyled.
        when {
            projectedNet != null -> {
                Text(
                    text = "Projected month-end net: ${money(projectedNet)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Text(
                    text = "Forecast — assumes no other spending",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            showForecastUpsell -> {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onForecastUpsell).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp),
                        tint = colors.textSecondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "See your projected month-end net — Premium",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
            lastMonthIncome != null -> {
                Text(
                    text = "Last month income: ${money(lastMonthIncome)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: BigDecimal, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalPlayfulColors.current.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = money(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Tab content composables ─────────────────────────────────────────────────

@Composable
private fun BreakdownSection(state: AnalysisUiState) {
    val colors = LocalPlayfulColors.current
    val donutSlices = state.slices.mapIndexed { index, slice ->
        DonutSlice(fraction = slice.fraction, color = sliceColor(slice.colorHex, index))
    }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Donut: 57% hole, gaps show the gradient behind (transparent track).
        DonutChart(
            slices = donutSlices,
            diameter = 188.dp,
            thickness = 40.dp,
            trackColor = Color.Transparent,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeartBullet(colors.accent, sizeDp = 15)
                Text(
                    text = money(state.totalExpense),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "spent this month",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Top-4 categories as a 2×2 glass grid with alternating leaf corners.
        val top = state.slices.take(4)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            top.chunked(2).forEachIndexed { rowIdx, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { colIdx, slice ->
                        val index = rowIdx * 2 + colIdx
                        CategoryGridCard(
                            name = slice.name,
                            amount = slice.amount,
                            percentLabel = slice.percentLabel,
                            color = sliceColor(slice.colorHex, index),
                            index = index,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (state.slices.size > 4) {
            Spacer(Modifier.height(12.dp))
            AllCategoriesPill(count = state.slices.size, expanded = expanded) { expanded = !expanded }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth()) {
                state.slices.forEachIndexed { index, slice ->
                    LegendRow(
                        color = sliceColor(slice.colorHex, index),
                        name = slice.name,
                        amount = slice.amount,
                        percentLabel = slice.percentLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    name: String,
    amount: BigDecimal,
    percentLabel: String,
    color: Color,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier,
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 20.dp, 8.dp),
        contentPadding = 13.dp,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                HeartBullet(color, sizeDp = 11)
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row(modifier = Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = money(amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = percentLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun AllCategoriesPill(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val colors = LocalPlayfulColors.current
    Box(
        modifier = Modifier
            .clip(LeafShapes.Chip)
            .background(colors.accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (expanded) "Show top 4" else "All $count categories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
        )
    }
}

@Composable
private fun LegendRow(color: Color, name: String, amount: BigDecimal, percentLabel: String) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeartBullet(color, sizeDp = 12)
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = money(amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No spending to analyze",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add some expenses for this period to see the breakdown.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExpenseFlowSection(flow: ExpenseFlowUi) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column {
            Text(
                "Expense Flow",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            ExpenseFlowChart(flow = flow)
        }
    }
}

@Composable
private fun ShortRangeFlowCard() {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Range too short to chart",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A single day can't show a spending curve. See the daily average below.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlowMetricsSection(metrics: FlowMetricsUi) {
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafMirrored(22.dp, 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricItem(
                label = if (metrics.perMonth) "Avg/month" else "Avg/day",
                amount = metrics.avg,
                modifier = Modifier.weight(1f),
            )
            metrics.projected?.let {
                // "At this pace" (not just "Projected") to disambiguate from the schedule-based
                // month-end forecast in the Summary (Item 37 Slice 2) — this one extrapolates the
                // current spend rate, that one sums upcoming recurring income/bills.
                MetricItem(label = "At this pace", amount = it, modifier = Modifier.weight(1f))
            }
            metrics.comparison?.let {
                ComparisonMetricItem(comparison = it, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComparisonMetricItem(comparison: FlowComparisonUi, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    // More spend than last period reads red (negative); less reads green (income); flat is neutral.
    val color = when {
        comparison.deltaSign > 0 -> colors.semantic.negative
        comparison.deltaSign < 0 -> colors.semantic.income
        else -> colors.textSecondary
    }
    val value = when {
        comparison.percentChange == null -> "New" // no prior spending to compare against
        comparison.deltaSign == 0 -> "0%"
        else -> {
            val arrow = if (comparison.deltaSign > 0) "▲" else "▼"
            "$arrow ${kotlin.math.abs(comparison.percentChange)}%"
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = comparison.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    color: Color = LocalPlayfulColors.current.textPrimary,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalPlayfulColors.current.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = money(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CalendarInsightsCard(biggestSpendDay: Int?, noSpendDayCount: Int) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No-spend days",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$noSpendDayCount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
            }
            if (biggestSpendDay != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Biggest spend",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Day $biggestSpendDay",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNetSection(
    calendarNet: CalendarNetUi,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafMirrored(22.dp, 9.dp),
    ) {
        Column {
            Text(
                "Daily Net",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            DailyNetCalendarChart(
                calendarNet = calendarNet,
                selectedDay = selectedDay,
                onDayClick = onDayClick,
            )
        }
    }
}

@Composable
private fun DayDetailCard(day: Int, dayUi: CalendarDayUi) {
    val colors = LocalPlayfulColors.current
    val income = BigDecimal.valueOf(dayUi.incomeFloat.toDouble())
    val expense = BigDecimal.valueOf(dayUi.expenseFloat.toDouble())
    val net = income - expense
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column {
            Text(
                text = "Day $day",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("Income", income, colors.semantic.income, Modifier.weight(1f))
                SummaryItem("Expense", expense, colors.semantic.negative, Modifier.weight(1f))
                SummaryItem(
                    label = "Net",
                    amount = net,
                    color = if (net.signum() < 0) colors.semantic.negative else colors.semantic.income,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun AnalysisPeriod.shortLabel(): String = when (this) {
    AnalysisPeriod.DAY -> "1D"
    AnalysisPeriod.WEEK -> "1W"
    AnalysisPeriod.MONTH -> "1M"
    AnalysisPeriod.QUARTER -> "3M"
    AnalysisPeriod.SEMI_ANNUAL -> "6M"
    AnalysisPeriod.ANNUAL -> "12M"
    AnalysisPeriod.ALL_TIME -> "ALL"
}
