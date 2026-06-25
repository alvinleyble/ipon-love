package com.iponlove.app.feature.widget.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.IponFilterChip
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var observeThemePreferences: ObserveThemePreferencesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreferences by observeThemePreferences()
                .collectAsState(initial = ThemePreferences())
            IponTheme(themePreferences = themePreferences) {
                QuickAddSheet(onDismiss = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    onDismiss: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TransactionType.INCOME, TransactionType.EXPENSE).forEach { type ->
                    IponFilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(type.label()) },
                    )
                }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount (₱)") },
                singleLine = true,
                isError = TransactionError.AMOUNT_NOT_POSITIVE in state.errors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            ChipPicker(
                label = "Category",
                options = state.categories.map { it.id to it.name },
                selectedId = state.categoryId,
                onSelect = viewModel::onCategoryChange,
                isError = TransactionError.CATEGORY_REQUIRED in state.errors,
            )

            ChipPicker(
                label = "Account",
                options = state.accounts.map { it.id to it.name },
                selectedId = state.accountId,
                onSelect = viewModel::onAccountChange,
                isError = TransactionError.ACCOUNT_REQUIRED in state.errors,
            )

            if (state.errors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.errors.forEach { error ->
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.save(onSaved = onDismiss) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ChipPicker(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        if (options.isEmpty()) {
            Text(
                text = "None available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                options.forEachIndexed { index, (id, name) ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    IponFilterChip(
                        selected = id == selectedId,
                        onClick = { onSelect(id) },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
}

private fun TransactionError.message(): String = when (this) {
    TransactionError.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
    TransactionError.ACCOUNT_REQUIRED -> "Choose an account"
    TransactionError.CATEGORY_REQUIRED -> "Choose a category"
    TransactionError.DESTINATION_REQUIRED -> "Choose a destination account"
    TransactionError.DESTINATION_SAME_AS_SOURCE -> "Destination must differ from source"
}
