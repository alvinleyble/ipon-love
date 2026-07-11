package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

/**
 * Single-figure summary card for a tab header (e.g. Accounts "Net assets"). Reusable across
 * tabs, but callers own the scoping of [amount] — never wire a cross-partner total into the
 * Combined view (ADR-0011: combined view shows shared spending, not partner balances).
 *
 * [onTogglePrivacyMode] is null by default (no eye icon shown); pass it to make this header one
 * of the global Privacy-mode (Item 15) entry points — [amount] itself always renders through
 * [money], so it masks correctly even for callers that don't show the toggle.
 */
@Composable
fun SummaryHeader(
    label: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    isPrivacyModeOn: Boolean = false,
    onTogglePrivacyMode: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = money(amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (onTogglePrivacyMode != null) {
                IconButton(onClick = onTogglePrivacyMode) {
                    Icon(
                        imageVector = if (isPrivacyModeOn) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (isPrivacyModeOn) "Show amounts" else "Hide amounts",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
