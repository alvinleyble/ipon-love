package com.iponlove.app.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.onboarding.domain.model.StarterBundle

/** Onboarding step 4/4 — à-la-carte starter-template picker (ADR-0024). All bundles are
 *  pre-checked; unchecking everything and continuing is a valid, fully-skippable path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTemplatesScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalPlayfulColors.current

    Box(modifier = Modifier.fillMaxSize().playfulBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textPrimary,
                        actionIconContentColor = colors.accent,
                    ),
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
                    color = colors.textSecondary,
                )
                StarterBundle.entries.forEachIndexed { index, bundle ->
                    BundleToggleRow(
                        index = index,
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
}

@Composable
private fun BundleToggleRow(index: Int, bundle: StarterBundle, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    bundle.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
