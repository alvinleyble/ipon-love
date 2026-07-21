package com.iponlove.app.feature.recurring.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.EntityChipRow
import com.iponlove.app.core.ui.EntityGrid
import com.iponlove.app.core.ui.EntityPickerOption
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulDialog
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringError
import com.iponlove.app.feature.recurring.presentation.components.RecurringCalendarChart
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current
    StartTourOnFirstVisit(TutorialTours.RECURRING)
    // Transparent chrome — the app-wide playfulBackground() from IponApp shows through.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text("Recurring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleViewMode,
                        modifier = Modifier.coachMarkTarget(TutorialTargets.RECURRING_CALENDAR),
                    ) {
                        if (state.viewMode == RecurringViewMode.LIST) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Calendar view")
                        } else {
                            Icon(Icons.Filled.List, contentDescription = "List view")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canAdd && state.viewMode == RecurringViewMode.LIST) {
                RecurringFab(
                    onClick = viewModel::startCreate,
                    modifier = Modifier.coachMarkTarget(TutorialTargets.RECURRING_ADD),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.viewMode == RecurringViewMode.CALENDAR ->
                    RecurringCalendarView(
                        state = state,
                        onPrev = viewModel::prevMonth,
                        onNext = viewModel::nextMonth,
                        onDayClick = viewModel::selectDay,
                        onLockedTap = { onOpenPremium(viewModel.onCalendarLockedTap()) },
                    )

                !state.canAdd ->
                    EmptyState(
                        title = "Set up an account and category first",
                        body = "Recurring rules need an account to charge and a category to file under.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                state.items.isEmpty() ->
                    EmptyState(
                        title = "No recurring rules yet",
                        body = "Tap + to automate a salary, bill, or subscription.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        RecurringRow(
                            item = item,
                            index = index,
                            onClick = { viewModel.startEdit(item.id) },
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onSkipNext = { viewModel.skipNext(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        RecurringEditorDialog(
            editor = editor,
            state = state,
            onAmountChange = viewModel::onAmountChange,
            onAccountChange = viewModel::onAccountChange,
            onCategoryChange = viewModel::onCategoryChange,
            onFrequencyChange = viewModel::onFrequencyChange,
            onIntervalChange = viewModel::onIntervalChange,
            onStartDateChange = viewModel::onStartDateChange,
            onEndDateChange = viewModel::onEndDateChange,
            onNoteChange = viewModel::onNoteChange,
            onAutoPostChange = viewModel::onAutoPostChange,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }
}

/** −4° accent squircle FAB (the Records/Savings/Manage recipe). */
@Composable
private fun RecurringFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .rotate(-4f)
            .size(56.dp)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add recurring rule",
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
        )
    }
}

private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
private fun RecurringCalendarView(
    state: RecurringUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (Int) -> Unit,
    onLockedTap: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val locked = state.calendarLocked
    Box(modifier = Modifier.fillMaxSize()) {
        RecurringCalendarContent(
            state = state,
            onPrev = onPrev,
            onNext = onNext,
            // Locked: the calendar is a non-interactive blurred preview — swallow day/step taps.
            onDayClick = if (locked) ({ }) else onDayClick,
            onStep = !locked,
            modifier = if (locked) Modifier.blur(12.dp) else Modifier,
        )
        if (locked) {
            // §8.2 blurred-preview soft gate: only the calendar *visualization* is Premium; the
            // list view + rule creation stay free. Tapping the preview routes to the paywall.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onLockedTap),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Premium",
                        tint = colors.accent,
                    )
                    Text(
                        "Calendar view is Premium",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Tap to unlock the recurring calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringCalendarContent(
    state: RecurringUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (Int) -> Unit,
    onStep: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Month stepper
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev, enabled = onStep) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = colors.accent,
                )
            }
            Text(
                text = state.calendarMonth.atDay(1).format(MONTH_FORMATTER),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            IconButton(onClick = onNext, enabled = onStep) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = colors.accent,
                )
            }
        }

        RecurringCalendarChart(
            month = state.calendarMonth,
            firingsByDay = state.firingsByDay,
            selectedDay = state.selectedDay,
            onDayClick = onDayClick,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (state.firingsByDay.isEmpty()) {
            Text(
                text = "No recurring rules fire this month",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 32.dp, end = 32.dp),
            )
        }

        // Selected day detail
        state.selectedDay?.let { day ->
            val rules = state.firingsByDay[day]
            if (!rules.isNullOrEmpty()) {
                Spacer(Modifier.height(16.dp))
                DayRulesCard(
                    day = day,
                    month = state.calendarMonth,
                    rules = rules,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayRulesCard(
    day: Int,
    month: java.time.YearMonth,
    rules: List<RecurringRuleListItem>,
) {
    val colors = LocalPlayfulColors.current
    val date = month.atDay(day)
    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column {
            Text(
                text = date.format(DATE_LABEL),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            rules.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = item.scheduleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = item.signedAmount(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = item.amountColor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(
    item: RecurringRuleListItem,
    index: Int,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipNext: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    var menuOpen by remember { mutableStateOf(false) }
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = item.scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = item.nextLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = item.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = item.amountColor(),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = colors.textSecondary,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (item.isPaused) {
                        DropdownMenuItem(
                            text = { Text("Resume") },
                            onClick = { menuOpen = false; onResume() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Pause") },
                            onClick = { menuOpen = false; onPause() },
                        )
                        DropdownMenuItem(
                            text = { Text("Skip next") },
                            onClick = { menuOpen = false; onSkipNext() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

// Modifier.padding() throws on negative values, so this can't just be a negative padding.
private fun Modifier.reduceHorizontalPadding(amount: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extraPx).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extraPx else constraints.maxWidth,
        ),
    )
    layout(placeable.width - extraPx, placeable.height) {
        placeable.place(x = -amount.roundToPx(), y = 0)
    }
}

@Composable
private fun RecurringEditorDialog(
    editor: RecurringEditorState,
    state: RecurringUiState,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onNoteChange: (String) -> Unit,
    onAutoPostChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val accountOptions = state.accounts.map {
        EntityPickerOption(it.id, it.name, it.icon?.let { k -> ACCOUNT_ICONS[k] }, it.color)
    }
    val categoryOptions = state.categories.map {
        EntityPickerOption(it.id, it.name, it.icon?.let { k -> CATEGORY_ICONS[k] }, it.color)
    }

    PlayfulDialog(
        onDismissRequest = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                if (editor.isEditing) "Edit recurring rule" else "New recurring rule",
                modifier = Modifier.reduceHorizontalPadding(8.dp),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .reduceHorizontalPadding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (${currencyGlyph()})") },
                    singleLine = true,
                    isError = RecurringError.AMOUNT_NOT_POSITIVE in editor.errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PickerLabel("Account")
                EntityChipRow(
                    options = accountOptions,
                    selectedId = editor.accountId,
                    onSelect = onAccountChange,
                )
                Spacer(Modifier.height(12.dp))
                PickerLabel("Category")
                EntityGrid(
                    options = categoryOptions,
                    selectedId = editor.categoryId,
                    onSelect = onCategoryChange,
                )
                Spacer(Modifier.height(12.dp))
                PickerLabel("Repeats")
                EntityChipRow(
                    options = RecurringFrequency.entries.map { EntityPickerOption(it.name, it.label()) },
                    selectedId = editor.frequency.name,
                    onSelect = { onFrequencyChange(RecurringFrequency.valueOf(it)) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.intervalText,
                    onValueChange = onIntervalChange,
                    label = { Text("Every N ${editor.frequency.unit()}") },
                    singleLine = true,
                    isError = RecurringError.INTERVAL_INVALID in editor.errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                DateField(
                    label = "Starts",
                    date = editor.startDate,
                    onPick = onStartDateChange,
                )
                Spacer(Modifier.height(12.dp))
                DateField(
                    label = "Ends",
                    date = editor.endDate,
                    placeholder = "No end date",
                    onPick = onEndDateChange,
                    onClear = { onEndDateChange(null) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                AutoPostToggle(autoPost = editor.autoPost, onAutoPostChange = onAutoPostChange)
                if (editor.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    editor.errors.forEach { error ->
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PickerLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
}

/**
 * Auto-post vs. confirm-on-arrival toggle (Item 37). OFF (the default) = the rule prompts you to
 * confirm each occurrence in Records before it's recorded; ON = it posts automatically, like the
 * legacy behaviour. Phrased around the OFF state since that's the default and the safer choice.
 */
@Composable
private fun AutoPostToggle(autoPost: Boolean, onAutoPostChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Post automatically", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (autoPost) {
                    "Records each occurrence for you on its date."
                } else {
                    "Asks you to confirm each occurrence in Records first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = autoPost, onCheckedChange = onAutoPostChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    onPick: (LocalDate) -> Unit,
    placeholder: String = "Pick a date",
    onClear: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }) {
                Text(date?.format(DATE_LABEL) ?: placeholder)
            }
            if (onClear != null && date != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }

    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(it.toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.DateRange,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp).rotate(-6f),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// DatePicker speaks UTC-midnight millis; convert through UTC so the calendar day is exact.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun RecurringRuleListItem.signedAmount(): String {
    val prefix = if (type == TransactionType.INCOME) "+" else "−"
    return prefix + money(amount)
}

@Composable
private fun RecurringRuleListItem.amountColor(): Color {
    val semantic = LocalPlayfulColors.current.semantic
    return if (type == TransactionType.INCOME) semantic.income else semantic.negative
}

private fun RecurringFrequency.label(): String = when (this) {
    RecurringFrequency.DAILY -> "Daily"
    RecurringFrequency.WEEKLY -> "Weekly"
    RecurringFrequency.MONTHLY -> "Monthly"
    RecurringFrequency.YEARLY -> "Annually"
}

private fun RecurringFrequency.unit(): String = when (this) {
    RecurringFrequency.DAILY -> "days"
    RecurringFrequency.WEEKLY -> "weeks"
    RecurringFrequency.MONTHLY -> "months"
    RecurringFrequency.YEARLY -> "years"
}

private fun RecurringError.message(): String = when (this) {
    RecurringError.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
    RecurringError.ACCOUNT_REQUIRED -> "Choose an account"
    RecurringError.CATEGORY_REQUIRED -> "Choose a category"
    RecurringError.INTERVAL_INVALID -> "Repeat interval must be at least 1"
    RecurringError.END_BEFORE_START -> "End date must be on or after the start date"
}
