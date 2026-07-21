package com.iponlove.app.feature.subscription.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/** The one-time price shown before purchase (§10.5 / D7). Hardcoded while dormant — a later slice
 *  (S11+) can swap in the live localized `ProductDetails.getFormattedPrice()` once the Play
 *  Console product exists. Play still charges the real localized amount regardless. */
private const val PRICE_TEXT = "₱249, one-time"

// Reused from PersonalizeScreen (the only other in-app link); kept local to avoid a cross-file
// refactor in this slice. A Terms-of-Service link is added at S11 once the legal page is live.
private const val PRIVACY_POLICY_URL = "https://alvinleyble.github.io/ipon-love-legal/privacy-policy.html"

private val PREMIUM_BENEFITS = listOf(
    "Unlock every color palette",
    "Higher limits on accounts, categories, budgets & savings goals",
    "More shared accounts, categories & goals with your partner",
    "The recurring calendar and extended analysis ranges",
    "Deep history beyond the last 12 months",
    "The built-in calculator, budget rollover & more receipt and note photos",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = LocalPlayfulColors.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    // Transparent chrome — the app-wide playfulBackground() from IponApp shows through.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text("Premium") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            when {
                state.loading -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }

                state.isPremium -> ActiveState(
                    restoreInProgress = state.restoreInProgress,
                    onRestore = viewModel::onRestore,
                )

                else -> UpsellState(
                    purchaseInProgress = state.purchaseInProgress,
                    restoreInProgress = state.restoreInProgress,
                    onBuy = { activity?.let(viewModel::onBuy) },
                    onRestore = viewModel::onRestore,
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Privacy Policy", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UpsellState(
    purchaseInProgress: Boolean,
    restoreInProgress: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val busy = purchaseInProgress || restoreInProgress
    Icon(
        Icons.Filled.Stars,
        contentDescription = null,
        tint = colors.accent,
        modifier = Modifier.size(48.dp).rotate(-8f),
    )
    Spacer(Modifier.height(12.dp))
    Text("Unlock more of Love, Ipon", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
    Spacer(Modifier.height(4.dp))
    Text(
        "Tracking your own money is always free. Premium adds more room and a few delightful extras.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )

    Spacer(Modifier.height(20.dp))
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column {
            PREMIUM_BENEFITS.forEachIndexed { index, benefit ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                BenefitRow(benefit)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onBuy,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (purchaseInProgress) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Get Premium — $PRICE_TEXT")
        }
    }
    TextButton(
        onClick = onRestore,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (restoreInProgress) "Restoring…" else "Restore purchase")
    }
}

@Composable
private fun ActiveState(
    restoreInProgress: Boolean,
    onRestore: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Stars,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(48.dp).rotate(-8f),
            )
            Spacer(Modifier.height(12.dp))
            Text("Premium — active", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Thank you! All Premium features are unlocked. Your one-time purchase never expires.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    TextButton(
        onClick = onRestore,
        enabled = !restoreInProgress,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (restoreInProgress) "Restoring…" else "Restore purchase")
    }
}

@Composable
private fun BenefitRow(text: String) {
    val colors = LocalPlayfulColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = colors.textPrimary,
        )
    }
}
