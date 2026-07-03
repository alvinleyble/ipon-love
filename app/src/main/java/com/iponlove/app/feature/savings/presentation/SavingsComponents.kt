package com.iponlove.app.feature.savings.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Goals pick from the shared category-icon set (savings, home, travel, gift, …). */
val GOAL_ICONS: Map<String, ImageVector> = CATEGORY_ICONS

fun goalIcon(key: String?): ImageVector? = key?.let { CATEGORY_ICONS[it] }

/** Slim progress bar for a goal, tinted by the goal's color (falls back to primary). */
@Composable
fun GoalProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().height(8.dp),
    )
}

/** A Material3 date picker in a dialog. [onPick] receives null only via the "Clear" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(
    initial: LocalDate?,
    onPick: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onPick(picked)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("Clear") }
        },
    ) {
        DatePicker(state = state)
    }
}
