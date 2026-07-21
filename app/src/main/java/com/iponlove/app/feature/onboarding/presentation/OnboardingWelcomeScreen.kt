package com.iponlove.app.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/** Onboarding step 1/4 — value-prop (ADR-0024). */
@Composable
fun OnboardingWelcomeScreen(onContinue: () -> Unit) {
    val colors = LocalPlayfulColors.current
    Box(modifier = Modifier.fillMaxSize().playfulBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(56.dp).rotate(-8f),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Love, Ipon",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Track your own money, and share a combined view with your partner when you're ready.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Built for the Philippines — PHP budgeting, offline-first, and a Partner Debt Tracker for IOUs.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
        }
    }
}
