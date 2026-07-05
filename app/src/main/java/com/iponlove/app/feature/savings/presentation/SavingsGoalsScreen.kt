package com.iponlove.app.feature.savings.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.feature.savings.domain.model.SavingsGoalProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
    viewModel: SavingsGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    StartTourOnFirstVisit(TutorialTours.SAVINGS, TutorialTours.SAVINGS_COUPLE)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Savings goals") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGoal,
                modifier = Modifier.coachMarkTarget(TutorialTargets.SAVINGS_ADD),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New goal")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.active.isEmpty() && state.archived.isEmpty() ->
                    EmptyGoals(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.active, key = { it.goal.id }) { item ->
                        GoalCard(item, onClick = { onOpenGoal(item.goal.id) })
                    }
                    if (state.archived.isNotEmpty()) {
                        item {
                            TextButton(onClick = viewModel::toggleShowArchived) {
                                Text(
                                    if (state.showArchived) "Hide archived (${state.archived.size})"
                                    else "Show archived (${state.archived.size})",
                                )
                            }
                        }
                        if (state.showArchived) {
                            items(state.archived, key = { it.goal.id }) { item ->
                                GoalCard(item, onClick = { onOpenGoal(item.goal.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(item: SavingsGoalProgress, onClick: () -> Unit) {
    val goal = item.goal
    val accent = parseHexColor(goal.color) ?: MaterialTheme.colorScheme.primary
    val icon = goalIcon(goal.icon) ?: Icons.Filled.Savings

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    color = accent.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        if (goal.isShared) {
                            Spacer(Modifier.size(6.dp))
                            SharedBadge()
                        }
                    }
                    Text(
                        "${formatPhp(item.savedAmount)} of ${formatPhp(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.reached) {
                    Icon(
                        Icons.Filled.Celebration,
                        contentDescription = "Reached",
                        tint = accent,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GoalProgressBar(progress = item.progress, color = accent)
        }
    }
}

@Composable
private fun EmptyGoals(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Savings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("No savings goals yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to set a target and start saving toward it — together or on your own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
