package com.iponlove.app.feature.savings.presentation

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
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.HeartTippedProgress
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulScreenTitle
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.savings.domain.model.SavingsGoalProgress
import kotlin.math.roundToInt

/**
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 5): a transparent-container [Scaffold] with a
 * [PlayfulScreenTitle] (matching the Analysis/Slice-1 standalone-screen pattern, since this screen
 * — unlike Accounts/Combined — owns its own top-level Scaffold rather than being hosted). Goal
 * cards alternate leaf shapes with a deepPlum (or the goal's own custom color) icon squircle and a
 * [HeartTippedProgress] bar; shared goals get the blush card treatment + a "Shared with partner"
 * caption. The privacy eye (v1.6.7 Item 7) rides the [PlayfulScreenTitle] actions slot, matching
 * Records' placement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
    viewModel: SavingsGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current

    StartTourOnFirstVisit(TutorialTours.SAVINGS, TutorialTours.SAVINGS_COUPLE)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(
                    title = "Savings goals",
                    actions = {
                        IconButton(onClick = viewModel::togglePrivacyMode) {
                            Icon(
                                imageVector = if (state.privacyModeEnabled) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (state.privacyModeEnabled) "Show amounts" else "Hide amounts",
                                tint = colors.textSecondary,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            SavingsFab(
                onClick = onCreateGoal,
                modifier = Modifier.coachMarkTarget(TutorialTargets.SAVINGS_ADD),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.active.isEmpty() && state.archived.isEmpty() ->
                    EmptyGoals(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.active, key = { _, item -> item.goal.id }) { index, item ->
                        GoalCard(item, index, onClick = { onOpenGoal(item.goal.id) })
                    }
                    if (state.archived.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                PlayfulChip(
                                    label = if (state.showArchived) "Hide archived (${state.archived.size})"
                                    else "Show archived (${state.archived.size})",
                                    selected = state.showArchived,
                                    onClick = viewModel::toggleShowArchived,
                                )
                            }
                        }
                        if (state.showArchived) {
                            itemsIndexed(state.archived, key = { _, item -> item.goal.id }) { index, item ->
                                GoalCard(item, index, onClick = { onOpenGoal(item.goal.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The center −4° squircle FAB, matching the global add-transaction FAB's identity language. */
@Composable
private fun SavingsFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            contentDescription = "New goal",
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
        )
    }
}

@Composable
private fun GoalCard(item: SavingsGoalProgress, index: Int, onClick: () -> Unit) {
    val goal = item.goal
    val colors = LocalPlayfulColors.current
    val surface = if (goal.isShared) PlayfulSurface.Blush else PlayfulSurface.Glass
    val ink = onPlayfulSurface(surface)
    val secondaryInk = if (surface == PlayfulSurface.Blush) colors.onBlushSecondary else colors.textSecondary
    val squircleColor = parseHexColor(goal.color) ?: colors.deepPlum
    val squircleInk = if (goal.color != null) Color.White else colors.onDeepPlum
    val progressColor = parseHexColor(goal.color) ?: if (goal.isShared) colors.deepPlum else colors.accent
    val icon = goalIcon(goal.icon) ?: Icons.Filled.Savings
    val squircleTilt = if (index % 2 == 0) -4f else 4f
    val percent = (item.progress * 100).roundToInt()

    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (goal.isArchived) 0.5f else 1f)
            .clickable(onClick = onClick),
        surface = surface,
        shape = LeafShapes.leafFor(index, 28.dp, 11.dp),
        contentPadding = 16.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .rotate(squircleTilt)
                        .clip(LeafShapes.IconSquircle)
                        .background(squircleColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = squircleInk,
                        modifier = Modifier.size(24.dp).rotate(-squircleTilt),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            goal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (goal.isShared) {
                            Spacer(Modifier.width(6.dp))
                            SharedBadge()
                        }
                    }
                    Text(
                        "${money(item.savedAmount)} of ${money(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ink,
                    )
                    if (item.reached) {
                        Icon(
                            Icons.Filled.Celebration,
                            contentDescription = "Reached",
                            tint = progressColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HeartTippedProgress(progress = item.progress, fillColor = progressColor)
            if (goal.isShared) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Shared with partner",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryInk,
                )
            }
        }
    }
}

@Composable
private fun EmptyGoals(modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Savings,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("No savings goals yet", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to set a target and start saving toward it — together or on your own.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
