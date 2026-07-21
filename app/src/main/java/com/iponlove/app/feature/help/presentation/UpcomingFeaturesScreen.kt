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

            val roadmap = listOf(
                "Google Sign-In (Q3 2026)" to "Sign up and log in with your Google account.",
                "Facebook Login (Q3 2026)" to "Sign up and log in with your Facebook account.",
                "AI companion (BYOK) (Not yet scheduled)" to "Ask questions about your spending using your own AI provider key.",
                "Website / web app (Q4 2026)" to "Access Love, Ipon from a browser, not just Android.",
                "Custom fonts (Q3 2026)" to "Personalize the app's typography, not just its colors.",
                "Profile & couple photo upload (Q3 2026)" to "Upload a real photo instead of accent color + initials.",
                "Premium (Q3 2026)" to "A one-time upgrade that unlocks extra features — core budgeting stays free.",
            )
            roadmap.forEachIndexed { index, (title, description) ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                RoadmapCard(index = index, title = title, description = description)
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
