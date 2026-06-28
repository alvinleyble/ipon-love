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
import com.iponlove.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
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
            Text("Help center coming soon", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "We're still writing the full help center. In the meantime, send us feedback via Settings → Beta feedback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))
            Text("Frequently asked questions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            FaqCard(
                question = "How do I pair with my partner?",
                answer = "Go to Couple → Overview and tap \"Create couple\". Share the invite code with your partner, who taps \"Join couple\" and enters the code.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                question = "How do I add a transaction?",
                answer = "Tap the + button on Records. Choose Income, Expense, or Transfer, fill in the amount, account, and category, then tap Save.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                question = "How does sync work?",
                answer = "Love, Ipon is offline-first — your data saves locally and syncs to the cloud in the background. Pull down on Records to sync manually.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                question = "How do I change the app theme?",
                answer = "Go to Settings, choose a color palette and light/dark mode, then tap Apply.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                question = "How do I set up app lock?",
                answer = "Go to Settings → Security. Set a 4-digit PIN and optionally enable biometric unlock.",
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
