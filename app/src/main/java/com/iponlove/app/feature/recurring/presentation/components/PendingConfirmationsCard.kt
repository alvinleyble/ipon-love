package com.iponlove.app.feature.recurring.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.LocalPrivacyMode
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation
import com.iponlove.app.feature.recurring.presentation.PendingConfirmationsViewModel
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * The Records "To confirm" card (Item 37) — confirm-on-arrival's free core. Renders nothing
 * when there's nothing pending, so it's safe to drop unconditionally at the top of Records.
 * Each row pre-fills the rule's amount in an editable field (the per-occurrence tweak), then
 * Confirm records it / Skip dismisses it; a header offers Confirm all / Skip all when there's
 * more than one.
 */
@Composable
fun PendingConfirmationsCard(
    modifier: Modifier = Modifier,
    viewModel: PendingConfirmationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    if (state.items.isEmpty()) return

    // Skip advances a single cursor, so it can only ever act on a rule's OLDEST pending
    // occurrence without stranding an earlier one — Confirm is order-independent (it drops off
    // via the materialized-id exclusion) but Skip is not. Only each rule's oldest row offers Skip.
    val skippableIds = remember(state.items) { skippableOccurrenceIds(state.items) }

    // Each row's own eye toggles the *global* Privacy mode (Item 15) — not a local per-row
    // reveal. All rows read the same LocalPrivacyMode, so tapping any one of them masks/reveals
    // every amount app-wide together (the row is just where the tap happens, not its own state).
    val privacyOn = LocalPrivacyMode.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "To confirm (${state.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.items.size > 1) {
                    TextButton(onClick = viewModel::skipAll) { Text("Skip all") }
                    TextButton(onClick = viewModel::confirmAll) { Text("Confirm all") }
                }
            }
            Text(
                text = "Recurring items that came due — record or skip each.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.items.forEach { item ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                PendingRow(
                    item = item,
                    showSkip = item.occurrenceId in skippableIds,
                    onTogglePrivacy = { viewModel.togglePrivacyMode(!privacyOn) },
                    onConfirm = { override -> viewModel.confirm(item.ruleId, item.date, override) },
                    onSkip = { viewModel.skip(item.ruleId, item.date) },
                )
            }
        }
    }
}

@Composable
private fun PendingRow(
    item: PendingConfirmation,
    showSkip: Boolean,
    onTogglePrivacy: () -> Unit,
    onConfirm: (BigDecimal?) -> Unit,
    onSkip: () -> Unit,
) {
    // Per-occurrence tweak, seeded with the rule amount and keyed on the occurrence so it stays
    // stable as sibling rows are confirmed/skipped out from under it.
    var amountText by rememberSaveable(item.occurrenceId) {
        mutableStateOf(item.amount.toPlainString())
    }
    // The eye lives on this row (like every other privacy entry point in the app), but it reads
    // and flips the one *global* Privacy mode (Item 15) — not a local per-row reveal. While
    // masked, Confirm posts the rule's own amount (amountText is never shown or typed into).
    val privacyOn = LocalPrivacyMode.current
    val editable = !privacyOn
    val kindLabel = if (item.type == TransactionType.INCOME) "Income" else "Bill"

    Column {
        Text(
            text = item.categoryName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$kindLabel due ${item.date.format(DATE_FORMAT)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (editable) amountText else money(item.amount), // "•••••" while masked
                onValueChange = { if (editable) amountText = it },
                readOnly = !editable,
                label = { Text("Amount (${currencyGlyph()})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    IconButton(onClick = onTogglePrivacy) {
                        Icon(
                            imageVector = if (privacyOn) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (privacyOn) "Show amounts" else "Hide amounts",
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onConfirm(if (editable) parseConfirmAmount(amountText) else null) },
                ) { Text("Confirm") }
                if (showSkip) TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

/**
 * The occurrence ids that may show a Skip button: each rule's **oldest** pending occurrence
 * (the first one, since [items] is globally oldest-first). Skipping only the oldest is what
 * keeps the single-cursor advance from stranding an earlier occurrence of the same rule.
 */
internal fun skippableOccurrenceIds(items: List<PendingConfirmation>): Set<String> {
    val rulesSeen = mutableSetOf<String>()
    return items.filter { rulesSeen.add(it.ruleId) }.map { it.occurrenceId }.toSet()
}

/**
 * Blank/invalid/non-positive → null, so the confirm falls back to the rule's template amount.
 * Grouping separators are stripped so a manually-typed "19,450" parses (defensive — the field
 * pre-fills a raw number and uses a decimal keypad, so this is a belt, not the norm).
 */
internal fun parseConfirmAmount(text: String): BigDecimal? =
    text.replace(",", "").replace(" ", "").trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
