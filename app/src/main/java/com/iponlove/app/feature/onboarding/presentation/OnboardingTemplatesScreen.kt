package com.iponlove.app.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.feature.onboarding.domain.model.StarterBundle

/** Onboarding step 3/3 — à-la-carte starter-template picker (ADR-0024). All bundles are
 *  pre-checked; unchecking everything and continuing is a valid, fully-skippable path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTemplatesScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starter setup") },
                actions = {
                    TextButton(
                        onClick = { viewModel.skip(onFinish) },
                        enabled = !state.isSaving,
                    ) { Text("Skip") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "We'll set up a few categories and accounts to get you started. Uncheck " +
                    "anything you don't want — you can always add more later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StarterBundle.entries.forEach { bundle ->
                BundleToggleRow(
                    bundle = bundle,
                    checked = bundle in state.selectedBundles,
                    onToggle = { viewModel.toggleBundle(bundle) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.finish(onFinish) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.selectedBundles.isEmpty()) "Continue" else "Add & continue")
            }
        }
    }
}

@Composable
private fun BundleToggleRow(bundle: StarterBundle, checked: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    bundle.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
