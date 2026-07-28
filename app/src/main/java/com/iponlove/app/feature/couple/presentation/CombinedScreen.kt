package com.iponlove.app.feature.couple.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.CoupleBanner
import com.iponlove.app.core.ui.FullScreenImagePager
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.HeartTippedProgress
import com.iponlove.app.core.ui.MonthStepperRow
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.msRounded
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.couple.domain.model.CombinedEntry
import com.iponlove.app.feature.couple.domain.model.MemberSpend
import com.iponlove.app.feature.couple.domain.usecase.spendSplit
import com.iponlove.app.feature.transactions.domain.model.TransactionType

private val VolunteerActivism = msRounded("volunteer_activism", "M549-53q8 2 17 2t17-2l297-91q0-54-30.5-80.5T762-251H587q-45 0-69.5-4.5T477-265l-61-21q-6-2-8.5-7.5T407-305q2-6 7.5-8.5t11.5-.5l59 19q24 8 44 11t46 3h73q8 0 13-5t5-13q0-23-16-45.5T604-378l-245-92q-5-2-10.5-3t-10.5-1h-83v337l294 84ZM40-140q0 25 17.5 42.5T100-80h34q25 0 42.5-17.5T194-140v-274q0-25-17.5-42.5T134-474h-34q-25 0-42.5 17.5T40-414v274Zm584-344.5q-11-4.5-20-12.5L482-616q-30-29-50-64.5T412-758q0-51 35.5-86.5T534-880q34 0 62 17.5t50 43.5q22-26 50-43.5t62-17.5q51 0 86.5 35.5T880-758q0 42-20 77.5T810-616L688-497q-9 8-20 12.5t-22 4.5q-11 0-22-4.5Z")

/**
 * Chrome-less Combined body — no Scaffold/TopAppBar. The Couple tab host
 * ([CoupleScreen]) provides the single scaffold; this renders the month stepper, the
 * pull-to-refresh list, and the read-only shared-budget summary (Item 35).
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 4): the You/Partner spend cards became an
 * overlapping blush/deepPlum leaf-card split with an accent knot + a two-color split bar; the
 * shared-budget summary and transaction rows moved onto [PlayfulCard]. [CoupleScreen]'s
 * TopAppBar/tab-row host is deliberately left untouched, same as the Manage host was left for
 * Accounts (Slice 3) — it converts once, when Debts is also restyled. The privacy eye now lives on
 * that host's [PlayfulScreenTitle] instead of a per-body header row (Item 16) — [money] still masks
 * the amounts here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CombinedBody(
    modifier: Modifier = Modifier,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: CombinedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = modifier.fillMaxSize().playfulBackground()) {
        if (state.isPaired && !state.isLoading) {
            MonthStepperRow(
                label = state.monthLabel,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = state.canGoToNextMonth,
                canGoPrevious = state.canGoToPreviousMonth,
                // Locked ← at the DEEP_HISTORY −12mo wall: log the touchpoint, route to the paywall.
                onPreviousLocked = { onOpenPremium(viewModel.onDeepHistoryUpsell()) },
            )
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::sync,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                !state.isPaired ->
                    EmptyState(
                        title = "Not paired yet",
                        body = "Pair with your partner to see your shared spending here.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> {
                    val colors = state.members.associate { it.id to it.accentColor }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The couple's face + headline spend leads the list (Item 9 Slice B, decision 7),
                        // above the shared-budgets summary.
                        item {
                            CombinedBanner(
                                coupleName = state.coupleName ?: "Us",
                                members = state.members,
                                bannerUrl = state.coupleBannerUrl,
                                bannerUnlocked = state.bannerUnlocked,
                            )
                        }
                        if (state.sharedBudgets.isNotEmpty()) {
                            item {
                                SharedBudgetsSummaryCard(
                                    budgets = state.sharedBudgets,
                                    monthLabel = state.monthLabel,
                                )
                            }
                        }
                        if (state.dayGroups.isEmpty()) {
                            item {
                                EmptyState(
                                    title = if (state.hasAnySharedActivityEver) {
                                        "No shared activity this month"
                                    } else {
                                        "No shared activity yet"
                                    },
                                    body = if (state.hasAnySharedActivityEver) {
                                        "Nothing shared between you this month."
                                    } else {
                                        "Your and your partner's non-private transactions show up here."
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                )
                            }
                        } else {
                            state.dayGroups.forEach { group ->
                                stickyHeader(key = group.label) { DayHeader(group.label) }
                                items(group.items, key = { it.id }) { entry ->
                                    CombinedRow(
                                        entry = entry,
                                        ownerColor = ownerColor(colors[entry.ownerId], entry.isMine),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DayHeader(label: String) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.backgroundBottom).padding(vertical = 8.dp),
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

/**
 * Read-only summary of the couple's shared budgets for the month (Item 35 / ADR-0047). A glance at
 * joint-budget progress; creating/editing lives in the Budgets tab (the caption points there). Only
 * shown when the couple has at least one shared budget this month. The "Manage" pill is decorative
 * (Item 35: non-navigating, no Manage sub-tab deep-link exists yet), not a real button.
 */
@Composable
private fun SharedBudgetsSummaryCard(
    budgets: List<SharedBudgetSummaryUi>,
    monthLabel: String,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = VolunteerActivism,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Shared budgets · $monthLabel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                PlayfulCard(surface = PlayfulSurface.DeepPlum, shape = LeafShapes.Badge, contentPadding = 0.dp) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onDeepPlum,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            budgets.forEach { budget ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = budget.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    HeartTippedProgress(
                        progress = budget.fraction,
                        fillColor = if (budget.isOverBudget) colors.semantic.negative else colors.accent,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${money(budget.spent)} of ${money(budget.limit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (budget.isOverBudget) {
                                "Over by ${money(budget.spent - budget.limit)}"
                            } else {
                                "${money(budget.remaining)} left"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (budget.isOverBudget) colors.semantic.negative else colors.textSecondary,
                        )
                    }
                }
            }
            Text(
                text = "Manage shared budgets in the Budgets tab.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * The couple banner section on the Combined view (v1.7.0 Item 9 Slice B, layout "C"): the shared
 * identity hero ([CoupleBanner] — gradient + both avatars + couple name, plus a circle photo
 * straddling the top edge when Item 10 is unlocked) with a [SpendStrip] folded in below it as a
 * darkened continuation of the same gradient, replacing the old You/Partner spend cards. Both
 * members present → hero + strip; the brief unresolved-partner transient reuses the single-accent
 * wash and hides the strip (decision 8). [money] masks the strip's peso figures under the global
 * privacy eye (now on the [CoupleScreen] host header, Item 16), while the % and split bar (a
 * ratio, not a peso figure) stay visible. The month is shown by [MonthStepperRow] above, not here
 * — the former "Spending · month" row was removed in the same Item 16 change.
 */
@Composable
private fun CombinedBanner(
    coupleName: String,
    members: List<MemberSpend>,
    bannerUrl: String?,
    bannerUnlocked: Boolean,
) {
    val me = members.firstOrNull { it.isMine }
    val partner = members.firstOrNull { !it.isMine }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoupleBanner(
            coupleName = coupleName,
            currentMotifKey = me?.avatarMotif,
            currentAccentHex = me?.accentColor,
            partnerMotifKey = partner?.avatarMotif,
            partnerAccentHex = partner?.accentColor,
            hasPartner = partner != null,
            bannerUrl = bannerUrl,
            bannerUnlocked = bannerUnlocked,
            modifier = Modifier.fillMaxWidth(),
            footer = if (me != null && partner != null) {
                { SpendStrip(me, partner) }
            } else {
                null
            },
        )
    }
}

/**
 * The spend strip under the identity hero (layout "C"): a semi-transparent black scrim over the
 * gradient the hero already carries — so it reads as a *darkened continuation* of the same identity
 * gradient, and the peso figures stay legible in white regardless of where each accent falls. Holds
 * both members' amounts inline with their share, and the two-color split bar with the heart on the
 * boundary.
 */
@Composable
private fun SpendStrip(me: MemberSpend, partner: MemberSpend) {
    val split = spendSplit(me.monthlyExpense, partner.monthlyExpense)
    val meColor = ownerColor(me.accentColor, isMine = true)
    val partnerColor = ownerColor(partner.accentColor, isMine = false)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpendLine(member = me, percent = split.mePercent, alignEnd = false, modifier = Modifier.weight(1f))
            SpendLine(member = partner, percent = split.partnerPercent, alignEnd = true, modifier = Modifier.weight(1f))
        }
        SplitBar(meFraction = split.meFraction, meColor = meColor, partnerColor = partnerColor)
    }
}

/** One member's spend inline: the peso amount (prominent), then "Name XX%" (softer). */
@Composable
private fun SpendLine(
    member: MemberSpend,
    percent: Int,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = money(member.monthlyExpense),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${member.label()} $percent%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

/** A two-color rounded pill sized by [meFraction], with a heart riding the split boundary. */
@Composable
private fun SplitBar(meFraction: Float, meColor: Color, partnerColor: Color, modifier: Modifier = Modifier) {
    val fraction = meFraction.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val heartPx = with(density) { 16.dp.toPx() }
        val offsetX = with(density) {
            (fraction * trackWidthPx - heartPx / 2f).coerceIn(0f, trackWidthPx - heartPx).toDp()
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(99.dp)),
        ) {
            Box(Modifier.weight(fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(meColor))
            Box(Modifier.weight((1f - fraction).coerceAtLeast(0.02f)).fillMaxHeight().background(partnerColor))
        }
        HeartBullet(
            color = Color.White,
            sizeDp = 16,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = offsetX),
        )
    }
}

@Composable
private fun CombinedRow(entry: CombinedEntry, ownerColor: Color) {
    var showReceipts by remember { mutableStateOf(false) }
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(LeafShapes.IconSquircle).background(ownerColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "${if (entry.isMine) "You" else "Partner"}  •  ${formatShortDate(entry.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (entry.attachmentUrls.isNotEmpty()) {
                // First receipt as a thumbnail; a count badge signals the rest. Tap opens the pager.
                Box(Modifier.clickable { showReceipts = true }) {
                    AsyncImage(
                        model = entry.attachmentUrls.first(),
                        contentDescription = "Receipt thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(LeafShapes.SmallChip),
                    )
                    if (entry.attachmentUrls.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accent,
                            modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${entry.attachmentUrls.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onAccent,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = entry.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = entry.amountColor(),
            )
        }
    }
    if (showReceipts && entry.attachmentUrls.isNotEmpty()) {
        FullScreenImagePager(
            models = entry.attachmentUrls,
            startIndex = 0,
            contentDescription = "Receipt full size",
            onDismiss = { showReceipts = false },
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A member's stored accent if usable, else a Playful accent/deepPlum fallback distinct per side. */
@Composable
private fun ownerColor(accentHex: String?, isMine: Boolean): Color {
    val colors = LocalPlayfulColors.current
    return parseHexColor(accentHex) ?: if (isMine) colors.accent else colors.deepPlum
}

private fun MemberSpend.label(): String = displayName ?: if (isMine) "You" else "Partner"

@Composable
private fun CombinedEntry.signedAmount(): String {
    val prefix = when (type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "−"
        TransactionType.TRANSFER -> ""
    }
    return prefix + money(amount)
}

@Composable
private fun CombinedEntry.amountColor(): Color {
    val colors = LocalPlayfulColors.current
    return when (type) {
        TransactionType.INCOME -> colors.semantic.income
        TransactionType.EXPENSE -> colors.semantic.negative
        TransactionType.TRANSFER -> colors.textSecondary
    }
}
