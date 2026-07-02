package com.iponlove.app.feature.calculator.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.feature.calculator.domain.CalculatorEngine
import com.iponlove.app.feature.calculator.domain.CalculatorOperator
import com.iponlove.app.feature.calculator.domain.CalculatorState

/**
 * Standalone arithmetic calculator (V1.5 slice 6, item 12) — no data/domain layer beyond the
 * pure [CalculatorEngine]; state lives locally since there's nothing to persist or sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    var state by remember { mutableStateOf(CalculatorState()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calculator") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CalculatorDisplay(state = state, modifier = Modifier.weight(1f))

            val row = Arrangement.spacedBy(8.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = row) {
                CalculatorKey("C", weight = 1f, colors = functionColors()) { state = CalculatorEngine.onClear(state) }
                CalculatorKey("⌫", weight = 1f, colors = functionColors()) { state = CalculatorEngine.onBackspace(state) }
                CalculatorKey("%", weight = 1f, colors = functionColors()) { state = CalculatorEngine.onPercent(state) }
                CalculatorKey("÷", weight = 1f, colors = operatorColors()) {
                    state = CalculatorEngine.onOperator(state, CalculatorOperator.DIVIDE)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = row) {
                CalculatorKey("7", weight = 1f) { state = CalculatorEngine.onDigit(state, '7') }
                CalculatorKey("8", weight = 1f) { state = CalculatorEngine.onDigit(state, '8') }
                CalculatorKey("9", weight = 1f) { state = CalculatorEngine.onDigit(state, '9') }
                CalculatorKey("×", weight = 1f, colors = operatorColors()) {
                    state = CalculatorEngine.onOperator(state, CalculatorOperator.MULTIPLY)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = row) {
                CalculatorKey("4", weight = 1f) { state = CalculatorEngine.onDigit(state, '4') }
                CalculatorKey("5", weight = 1f) { state = CalculatorEngine.onDigit(state, '5') }
                CalculatorKey("6", weight = 1f) { state = CalculatorEngine.onDigit(state, '6') }
                CalculatorKey("−", weight = 1f, colors = operatorColors()) {
                    state = CalculatorEngine.onOperator(state, CalculatorOperator.SUBTRACT)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = row) {
                CalculatorKey("1", weight = 1f) { state = CalculatorEngine.onDigit(state, '1') }
                CalculatorKey("2", weight = 1f) { state = CalculatorEngine.onDigit(state, '2') }
                CalculatorKey("3", weight = 1f) { state = CalculatorEngine.onDigit(state, '3') }
                CalculatorKey("+", weight = 1f, colors = operatorColors()) {
                    state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = row) {
                CalculatorKey("±", weight = 1f) { state = CalculatorEngine.onToggleSign(state) }
                CalculatorKey("0", weight = 1f) { state = CalculatorEngine.onDigit(state, '0') }
                CalculatorKey(".", weight = 1f) { state = CalculatorEngine.onDecimal(state) }
                CalculatorKey("=", weight = 1f, colors = operatorColors()) {
                    state = CalculatorEngine.onEquals(state)
                }
            }
        }
    }
}

@Composable
private fun CalculatorDisplay(state: CalculatorState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        val expression = state.storedValue?.let { stored ->
            state.pendingOperator?.let { op -> "${stored.toPlainString()} ${op.symbol}" }
        }
        if (expression != null) {
            Text(
                expression,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Text(
            state.display,
            style = MaterialTheme.typography.displayMedium,
            color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            fontSize = 40.sp,
        )
    }
}

@Composable
private fun operatorColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun functionColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun RowScope.CalculatorKey(
    label: String,
    weight: Float,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .weight(weight)
            .aspectRatio(1f),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
