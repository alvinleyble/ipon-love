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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingFeaturesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
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
                "There's no timeline for any of these yet — just a look at what's on our radar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            RoadmapCard(
                title = "Google Sign-In",
                description = "Sign up and log in with your Google account.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "Facebook Login",
                description = "Sign up and log in with your Facebook account.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "AI companion (BYOK)",
                description = "Ask questions about your spending using your own AI provider key.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "Password vault",
                description = "Store your passwords securely alongside your finances.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "Voice recording storage",
                description = "Attach voice notes to your notes and transactions.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "iOS",
                description = "Love, Ipon on iPhone.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "CSV / PDF export",
                description = "Export your transactions and reports for backup or sharing.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "Custom fonts",
                description = "Personalize the app's typography, not just its colors.",
            )
            Spacer(Modifier.height(8.dp))
            RoadmapCard(
                title = "Profile & couple photo upload",
                description = "Upload a real photo instead of accent color + initials.",
            )
        }
    }
}

@Composable
private fun RoadmapCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
