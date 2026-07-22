package com.iponlove.app.feature.help.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingFeaturesScreen(onBack: () -> Unit) {
    val colors = LocalPlayfulColors.current
    // Transparent chrome — the app-wide playfulBackground() from IponApp shows through.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
                title = { Text("Upcoming features") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Rough targets below — these are planning estimates, not commitments, and can shift. Items marked \"Not yet scheduled\" don't have one yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(20.dp))

            val roadmap = ROADMAP.sortedWith(compareBy { it.target })
            roadmap.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                RoadmapCard(
                    index = index,
                    title = "${item.title} (${item.target.label})",
                    description = item.description,
                )
            }
        }
    }
}

@Composable
private fun RoadmapCard(index: Int, title: String, description: String) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

private data class RoadmapItem(val title: String, val description: String, val target: RoadmapTarget)

/**
 * Sorts latest-first: farthest-out scheduled quarter, then not-yet-scheduled, then shipped items
 * (newest version first) — so a feature keeps its card (with a version note) instead of being
 * deleted once it ships, per Alvin's 2026-07-23 call to stop pruning shipped entries outright.
 */
private sealed class RoadmapTarget(val label: String) : Comparable<RoadmapTarget> {
    private val rank: Int
        get() = when (this) {
            is Scheduled -> 0
            NotYetScheduled -> 1
            is Shipped -> 2
        }

    override fun compareTo(other: RoadmapTarget): Int {
        rank.compareTo(other.rank).let { if (it != 0) return it }
        return when {
            this is Scheduled && other is Scheduled -> other.quarterIndex.compareTo(quarterIndex)
            this is Shipped && other is Shipped -> -compareVersions(version, other.version)
            else -> 0
        }
    }

    class Scheduled(val year: Int, val quarter: Int) : RoadmapTarget("Q$quarter $year") {
        val quarterIndex = year * 4 + quarter
    }

    data object NotYetScheduled : RoadmapTarget("Not yet scheduled")

    class Shipped(val version: String) : RoadmapTarget(version)
}

private fun compareVersions(a: String, b: String): Int {
    val partsA = a.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val partsB = b.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(partsA.size, partsB.size)) {
        val cmp = partsA.getOrElse(i) { 0 }.compareTo(partsB.getOrElse(i) { 0 })
        if (cmp != 0) return cmp
    }
    return 0
}

private val ROADMAP = listOf(
    RoadmapItem(
        title = "Website / web app",
        description = "Access Love, Ipon from a browser, not just Android.",
        target = RoadmapTarget.Scheduled(2026, 4),
    ),
    RoadmapItem(
        title = "Google Sign-In",
        description = "Sign up and log in with your Google account.",
        target = RoadmapTarget.Scheduled(2026, 3),
    ),
    RoadmapItem(
        title = "Facebook Login",
        description = "Sign up and log in with your Facebook account.",
        target = RoadmapTarget.Scheduled(2026, 3),
    ),
    RoadmapItem(
        title = "Custom fonts",
        description = "Personalize the app's typography, not just its colors.",
        target = RoadmapTarget.Scheduled(2026, 3),
    ),
    RoadmapItem(
        title = "Profile & couple photo upload",
        description = "Upload a real photo instead of accent color + initials.",
        target = RoadmapTarget.Scheduled(2026, 3),
    ),
    RoadmapItem(
        title = "Premium",
        description = "A one-time upgrade that unlocks extra features — core budgeting stays free.",
        target = RoadmapTarget.Scheduled(2026, 3),
    ),
    RoadmapItem(
        title = "AI companion (BYOK)",
        description = "Ask questions about your spending using your own AI provider key.",
        target = RoadmapTarget.NotYetScheduled,
    ),
    RoadmapItem(
        title = "Restart fresh",
        description = "Reset your transactions, recurring rules, budgets, and savings progress " +
            "without deleting your account or losing your accounts and categories.",
        target = RoadmapTarget.Shipped("v1.6.5"),
    ),
    RoadmapItem(
        title = "Choose your currency symbol",
        description = "Swap the ₱ symbol for another currency's symbol. Display only — no " +
            "conversion, and amounts stay in one currency underneath.",
        target = RoadmapTarget.Shipped("v1.6.5"),
    ),
    RoadmapItem(
        title = "Shared accounts",
        description = "Both partners contribute to and track the same account together.",
        target = RoadmapTarget.Shipped("v1"),
    ),
    RoadmapItem(
        title = "Shared budget",
        description = "Set a joint monthly budget you and your partner track together.",
        target = RoadmapTarget.Shipped("v1"),
    ),
)
