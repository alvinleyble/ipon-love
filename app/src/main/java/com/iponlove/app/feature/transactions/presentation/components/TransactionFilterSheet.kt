package com.iponlove.app.feature.transactions.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.presentation.FilterOption

/**
 * The shared transaction filter sheet (v1.7.0 Item 7, extracted from Records for Item 6's
 * re-grill 2026-07-24). Four sections — Type, Category, Account (each a wrapping chip grid, empty
 * selection = All) + an absolute Min/Max amount range. Holds a **draft** seeded from [applied];
 * **Apply** commits it, **dismissing discards** it, **Clear** resets to none. Apply is disabled
 * while the range is inverted.
 *
 * One component, two callers (Records + Export) — extracted so the two never drift apart, per the
 * "no second filter UI" principle from the original Item 6 grill.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFilterSheet(
    applied: TransactionFilter,
    categories: List<FilterOption>,
    accounts: List<FilterOption>,
    onApply: (TransactionFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var categoryIds by remember { mutableStateOf(applied.categoryIds) }
    var accountIds by remember { mutableStateOf(applied.accountIds) }
    var types by remember { mutableStateOf(applied.types) }
    var minText by remember { mutableStateOf(applied.minAmount?.toPlainString().orEmpty()) }
    var maxText by remember { mutableStateOf(applied.maxAmount?.toPlainString().orEmpty()) }

    val min = TransactionFilter.parseBound(minText)
    val max = TransactionFilter.parseBound(maxText)
    val rangeInverted = min != null && max != null && min > max

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )

            FilterSection(title = "Type", showAllHint = types.isEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransactionType.entries.forEach { t ->
                        PlayfulChip(
                            label = t.filterLabel(),
                            selected = t in types,
                            onClick = { types = types.toggle(t) },
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                FilterSection(title = "Category", showAllHint = categoryIds.isEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { opt ->
                            PlayfulChip(
                                label = opt.label,
                                selected = opt.id in categoryIds,
                                onClick = { categoryIds = categoryIds.toggle(opt.id) },
                            )
                        }
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                FilterSection(title = "Account", showAllHint = accountIds.isEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { opt ->
                            PlayfulChip(
                                label = opt.label,
                                selected = opt.id in accountIds,
                                onClick = { accountIds = accountIds.toggle(opt.id) },
                            )
                        }
                    }
                }
            }

            FilterSection(title = "Amount range", showAllHint = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min") },
                        singleLine = true,
                        isError = rangeInverted,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max") },
                        singleLine = true,
                        isError = rangeInverted,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rangeInverted) {
                    Text(
                        "Minimum can't be larger than maximum",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.semantic.negative,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(
                    onClick = {
                        onApply(
                            TransactionFilter(
                                categoryIds = categoryIds,
                                accountIds = accountIds,
                                types = types,
                                minAmount = min,
                                maxAmount = max,
                            ),
                        )
                    },
                    enabled = !rangeInverted,
                    modifier = Modifier.weight(1f),
                ) { Text("Apply") }
            }
        }
    }
}

/** One labelled filter section; shows a subtle "All" hint beside the title when nothing in it is
 *  selected — empty selection *is* all, so no explicit "All" chip. */
@Composable
private fun FilterSection(
    title: String,
    showAllHint: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            if (showAllHint) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "All",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        content()
    }
}

private fun TransactionType.filterLabel(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
}

/** Toggles membership of [item] in the set (add if absent, remove if present) — the chip semantics. */
private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
