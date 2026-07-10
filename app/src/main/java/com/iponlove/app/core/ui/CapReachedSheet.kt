package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The presentation model for the count-cap upsell (§10.3 "hard count-cap hit"). [entityLabel] is the
 * plural noun for the copy ("accounts", "budgets"); [freeLimit]/[premiumMax] come straight off the
 * gate's [com.iponlove.app.core.entitlement.CapCheck.Blocked]. A ViewModel sets this on its UiState
 * when a create is blocked and clears it on dismiss — the screen renders [CapReachedSheet] from it.
 */
data class UpsellPrompt(
    val entityLabel: String,
    val freeLimit: Int,
    val premiumMax: Int,
)

/**
 * The one shared upsell surface every count-cap gate reuses (S7). Shown when creating past the free
 * cap is blocked: a plain explanation + a "Get Premium" CTA that routes to the paywall ([onUpgrade])
 * and a dismiss. Block-on-create only — existing over-cap data is never touched (T1 freeze), so this
 * is purely a create-time nudge, never a destructive prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapReachedSheet(
    prompt: UpsellPrompt,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "You've reached the free limit",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "Free includes up to ${prompt.freeLimit} ${prompt.entityLabel}. " +
                    "Go Premium for up to ${prompt.premiumMax}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Get Premium")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
    }
}
