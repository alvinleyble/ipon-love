package com.iponlove.app.feature.savings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.EntityColorPicker
import com.iponlove.app.core.ui.icons.IconPicker
import com.iponlove.app.core.ui.parseHexColor
import java.time.format.DateTimeFormatter

private val goalDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    onBack: () -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: GoalEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val tint = parseHexColor(state.color) ?: MaterialTheme.colorScheme.primary
    val readOnly = state.isPartnerGoal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New goal" else "Edit goal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!readOnly) {
                        TextButton(onClick = { viewModel.save(onBack) }) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (state.missing) {
            Text("Goal not found", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Goal name") },
                singleLine = true,
                enabled = !readOnly,
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text("Name is required") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.targetText,
                onValueChange = viewModel::onTargetChange,
                label = { Text("Target amount (${currencyGlyph()})") },
                singleLine = true,
                enabled = !readOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.targetError,
                supportingText = if (state.targetError) ({ Text("Enter an amount greater than 0") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = !readOnly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(state.targetDate?.let { "Target date: ${goalDate.format(it)}" } ?: "Add a target date (optional)")
            }

            if (state.isPaired && !readOnly) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Share with partner", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Both of you can contribute and see the progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.isShared, onCheckedChange = { viewModel.toggleShared() })
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            IconPicker(
                icons = GOAL_ICONS,
                selectedKey = state.icon,
                tintColor = tint,
                onSelect = { key -> viewModel.onIconChange(if (key == state.icon) null else key) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            EntityColorPicker(
                selectedHex = state.color,
                onSelect = { hex -> viewModel.onColorChange(if (hex == state.color) null else hex) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        PickDateDialog(
            initial = state.targetDate,
            onPick = { picked -> viewModel.onDateChange(picked); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    state.upsell?.let { prompt ->
        CapReachedSheet(
            prompt = prompt,
            onDismiss = viewModel::dismissUpsell,
            onUpgrade = { onOpenPremium(viewModel.onUpsellUpgrade()) },
        )
    }
}
