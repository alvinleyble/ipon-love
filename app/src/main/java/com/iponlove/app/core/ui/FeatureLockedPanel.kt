package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared full-panel soft-gate wall (S9): a centered lock + explanation + "Get Premium" CTA,
 * shown in place of a whole gated module while it's locked. For per-element soft gates (a locked
 * palette swatch, a blurred calendar) the surface renders its own inline lock affordance instead;
 * this is the "entire module is Premium" shape.
 *
 * Currently unused by any shipped surface: the Calculator was its one consumer until ADR-0058 made
 * that module a bubble with no full screen to wall off (a locked user now goes straight to the
 * paywall at spawn time). Kept as the designed S9 primitive for the next whole-module gate — it is
 * `fillMaxSize()` by construction, which is precisely why the bubble could not host it.
 */
@Composable
fun FeatureLockedPanel(
    title: String,
    body: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onUpgrade, modifier = Modifier.padding(top = 24.dp)) {
            Text("Get Premium")
        }
    }
}
