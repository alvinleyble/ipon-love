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
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
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
            Text(
                "Help center coming soon",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We're still writing the full help center. In the meantime, send us feedback via Settings → Beta feedback.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(28.dp))
            Text(
                "Frequently asked questions",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))

            FaqCard(
                index = 0,
                question = "How do I pair with my partner?",
                answer = "Go to Couple → Overview and tap \"Create couple\". Share the invite code with your partner, who taps \"Join couple\" and enters the code.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                index = 1,
                question = "How do I add a transaction?",
                answer = "Tap the + button on Records. Choose Income, Expense, or Transfer, fill in the amount, account, and category, then tap Save.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                index = 2,
                question = "How does sync work?",
                answer = "Love, Ipon is offline-first — your data saves locally and syncs to the cloud in the background. Pull down on Records to sync manually.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                index = 3,
                question = "How do I change the app theme?",
                answer = "Go to Settings, choose a color palette and light/dark mode, then tap Apply.",
            )
            Spacer(Modifier.height(8.dp))
            FaqCard(
                index = 4,
                question = "How do I set up app lock?",
                answer = "Go to Settings → Security. Set a 4-digit PIN and optionally enable biometric unlock.",
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun FaqCard(index: Int, question: String, answer: String) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
    ) {
        Column {
            Text(question, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
