package com.iponlove.app.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CurrencyGrid
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/**
 * Onboarding step 2/4 — display-currency symbol picker (Item 27, split from Item 18). ₱
 * ([CurrencySymbol.DEFAULT]) is pre-selected; a plain tap-through on "Continue" keeps peso, the
 * PH-market default. Existing beta testers (already past onboarding) are unaffected — they use
 * Settings → Personalize → Currency instead.
 */
@Composable
fun OnboardingCurrencyScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingCurrencyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val colors = LocalPlayfulColors.current
    Box(modifier = Modifier.fillMaxSize().playfulBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Choose your currency symbol",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is just a display label — every amount stays the same underneath, and " +
                    "you can change it later from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            CurrencyGrid(
                symbols = CurrencySymbol.entries,
                selected = state.selected,
                onSelect = viewModel::selectSymbol,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}
