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
import com.iponlove.app.core.ui.MotifPicker
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * Onboarding step 2/5 — motif-avatar picker (Item 42). The motif-avatar picker (v1.6.7 Item 3
 * Leg 1) previously only lived in Settings → Profile, undiscoverable to a new user; this gives it
 * a gentle, skippable first surface. Heart ([com.iponlove.app.core.ui.AvatarMotif.Default]) is
 * pre-selected; a plain tap-through on "Continue" keeps it. Existing beta testers (already past
 * onboarding) are unaffected — they use Settings → Profile instead.
 */
@Composable
fun OnboardingMotifScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingMotifViewModel = hiltViewModel(),
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
                "What symbol represents you best?",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is just for you — it shows up next to your name, and you can change it " +
                    "anytime from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            MotifPicker(
                selectedKey = state.selected,
                accentHex = state.accentColor,
                onSelect = viewModel::selectMotif,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}
