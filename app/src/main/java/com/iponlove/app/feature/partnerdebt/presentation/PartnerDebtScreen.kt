package com.iponlove.app.feature.partnerdebt.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import com.iponlove.app.feature.partnerdebt.domain.model.NetDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDebtScreen(
    onBack: () -> Unit,
    viewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partner debts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isPaired) {
                FloatingActionButton(onClick = viewModel::startAddDebt) {
                    Icon(Icons.Filled.Add, contentDescription = "Add debt")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                !state.isPaired ->
                    EmptyState(
                        title = "Not paired yet",
                        body = "Pair with your partner to track who owes whom.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { NetSummaryCard(state.net) }
                    if (state.debts.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No debts yet",
                                body = "Add an IOU when one of you covers something for the other.",
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            )
                        }
                    } else {
                        items(state.debts, key = { it.id }) { debt ->
                            DebtCard(
                                debt = debt,
                                onPay = { viewModel.startPayment(debt) },
                                onDelete = { viewModel.removeDebt(debt.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.addEditor?.let { editor ->
        AddDebtDialog(
            editor = editor,
            partnerName = state.partnerName,
            onDirectionChange = viewModel::onDirectionChange,
            onAmountChange = viewModel::onDebtAmountChange,
            onDescriptionChange = viewModel::onDebtDescriptionChange,
            onSave = viewModel::saveDebt,
            onCancel = viewModel::cancelAddDebt,
        )
    }

    state.paymentEditor?.let { editor ->
        PaymentDialog(
            editor = editor,
            onAmountChange = viewModel::onPaymentAmountChange,
            onNoteChange = viewModel::onPaymentNoteChange,
            onSave = viewModel::savePayment,
            onCancel = viewModel::cancelPayment,
        )
    }
}

@Composable
private fun NetSummaryCard(net: DebtNet?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Net balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val partner = net?.counterpartName ?: "your partner"
            when (net?.direction) {
                NetDirection.I_OWE -> {
                    Text("You owe $partner", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatPhp(net.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                NetDirection.OWED_TO_ME -> {
                    Text("$partner owes you", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatPhp(net.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                else ->
                    Text("All settled up", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DebtCard(debt: DebtItem, onPay: () -> Unit, onDelete: () -> Unit) {
    val partner = debt.counterpartName ?: "Partner"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (debt.iAmBorrower) "You owe $partner" else "$partner owes you",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    debt.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete debt")
                }
            }

            if (debt.isSettled) {
                Text(
                    text = "Settled · ${formatPhp(debt.original)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                LinearProgressIndicator(
                    progress = { debt.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatPhp(debt.remaining)} of ${formatPhp(debt.original)} left",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onPay) { Text("Record payment") }
                }
            }

            if (debt.payments.isNotEmpty()) {
                debt.payments.forEach { payment ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = payment.note ?: "Payment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatPhp(payment.amount)} · ${formatShortDate(payment.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDebtDialog(
    editor: AddDebtEditorState,
    partnerName: String,
    onDirectionChange: (DebtDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IponFilterChip(
                        selected = editor.direction == DebtDirection.I_OWE,
                        onClick = { onDirectionChange(DebtDirection.I_OWE) },
                        label = { Text("I owe $partnerName") },
                    )
                    IponFilterChip(
                        selected = editor.direction == DebtDirection.THEY_OWE,
                        onClick = { onDirectionChange(DebtDirection.THEY_OWE) },
                        label = { Text("$partnerName owes me") },
                    )
                }
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    isError = editor.amountError,
                    supportingText = if (editor.amountError) {
                        { Text("Enter an amount greater than zero") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("What's it for? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PaymentDialog(
    editor: PaymentEditorState,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${formatPhp(editor.remaining)} left on ${editor.debtLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    isError = editor.amountError,
                    supportingText = if (editor.amountError) {
                        { Text("Enter an amount up to ${formatPhp(editor.remaining)}") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
