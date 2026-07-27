package com.iponlove.app.feature.partnerdebt.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.HeartTippedProgress
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulDialog
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.ellipsize
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPaymentItem
import com.iponlove.app.feature.partnerdebt.domain.model.NetDirection
import java.math.BigDecimal

/** Keeps a long partner name from overflowing the tight chip/header layouts below. */
private const val PARTNER_NAME_DISPLAY_MAX = 15

/**
 * Chrome-less Debts body — no Scaffold/TopAppBar/FAB. The Couple tab host ([CoupleScreen])
 * provides the scaffold and an Add-debt FAB (visible only on this tab while paired).
 * The dialogs are rendered here so the ViewModel stays the sole owner of dialog state.
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6e): the net summary became a Glass leaf hero
 * (semantic-tinted amount + the Item 7 privacy eye), debt rows are owner-tinted [PlayfulCard]s with
 * a [HeartTippedProgress] bar, and the three editor dialogs adopt 6-PD's [PlayfulDialog]. The
 * owner tint uses each side's ADR-0014 accent color (mine for debts I owe, my partner's for theirs).
 * The [CoupleScreen] host chrome converts in the same commit, as this is the last Couple tab.
 */
@Composable
fun PartnerDebtBody(
    modifier: Modifier = Modifier,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Box(modifier.fillMaxSize().playfulBackground()) {
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NetSummaryCard(
                        net = state.net,
                        isPrivacyModeOn = state.privacyModeEnabled,
                        onTogglePrivacyMode = viewModel::togglePrivacyMode,
                    )
                }
                if (state.debts.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No debts yet",
                            body = "Add an IOU when one of you covers something for the other.",
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                } else {
                    itemsIndexed(state.debts, key = { _, it -> it.id }) { index, debt ->
                        DebtCard(
                            debt = debt,
                            index = index,
                            // Owner tint by who owes: my accent for debts I owe, my partner's for theirs.
                            ownerColor = ownerColor(
                                accentHex = if (debt.iAmBorrower) state.myAccentColor else state.partnerAccentColor,
                                isMine = debt.iAmBorrower,
                            ),
                            onSettle = { viewModel.startSettle(debt) },
                            onReceive = { payment -> viewModel.startReceive(debt, payment) },
                            onDelete = { viewModel.removeDebt(debt.id) },
                        )
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        is DebtDialog.AddDebt -> AddDebtDialog(
            editor = dialog,
            partnerName = state.partnerName,
            onDirectionChange = viewModel::onDirectionChange,
            onAmountChange = viewModel::onDebtAmountChange,
            onDescriptionChange = viewModel::onDebtDescriptionChange,
            onSave = viewModel::saveDebt,
            onCancel = viewModel::cancelDialog,
        )

        is DebtDialog.Settle -> SettleDialog(
            editor = dialog,
            accounts = state.accounts,
            onAmountChange = viewModel::onSettleAmountChange,
            onAccountChange = viewModel::onSettleAccountChange,
            onNoteChange = viewModel::onSettleNoteChange,
            onToggleTarget = viewModel::onToggleSettleTarget,
            onPayFull = viewModel::onPayFullOutstanding,
            onSave = viewModel::saveSettle,
            onCancel = viewModel::cancelDialog,
        )

        is DebtDialog.Receive -> ReceiveDialog(
            editor = dialog,
            accounts = state.accounts,
            onAccountChange = viewModel::onReceiveAccountChange,
            onSave = viewModel::saveReceive,
            onCancel = viewModel::cancelDialog,
        )

        null -> Unit
    }

    state.upsell?.let { prompt ->
        CapReachedSheet(
            prompt = prompt,
            onDismiss = viewModel::dismissUpsell,
            onUpgrade = { onOpenPremium(viewModel.onUpsellUpgrade()) },
        )
    }
}

/**
 * The net-balance hero (v1.6.7 Item 8 Slice 6e): a Glass leaf-squircle [PlayfulCard] with a
 * translucent heart accent, the direction sentence, and the amount tinted by net direction —
 * `semantic.negative` when I owe, `semantic.income` when I'm owed (a Glass hero, not a fixed
 * accent→deepPlum gradient, so this red/green net cue survives the reskin). Carries the Item 7
 * privacy eye (masking is global — [PartnerDebtViewModel.togglePrivacyMode]).
 */
@Composable
private fun NetSummaryCard(
    net: DebtNet?,
    isPrivacyModeOn: Boolean,
    onTogglePrivacyMode: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leaf(30.dp, 12.dp),
        tiltDegrees = -0.6f,
        contentPadding = 18.dp,
    ) {
        // Oversized translucent heart accent, clipped by the card's own leaf-squircle shape.
        HeartBullet(
            color = colors.accent.copy(alpha = 0.10f),
            sizeDp = 88,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 18.dp)
                .rotate(-10f),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Net balance",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onTogglePrivacyMode) {
                    Icon(
                        imageVector = if (isPrivacyModeOn) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (isPrivacyModeOn) "Show amounts" else "Hide amounts",
                        tint = colors.textSecondary,
                    )
                }
            }
            val partner = (net?.counterpartName ?: "your partner").ellipsize(PARTNER_NAME_DISPLAY_MAX)
            when (net?.direction) {
                NetDirection.I_OWE -> {
                    Text(
                        "You owe $partner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    HeroAmount(money(net.amount), colors.semantic.negative)
                }

                NetDirection.OWED_TO_ME -> {
                    Text(
                        "$partner owes you",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    HeroAmount(money(net.amount), colors.semantic.income)
                }

                else ->
                    Text(
                        "All settled up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
            }
        }
    }
}

@Composable
private fun HeroAmount(text: String, color: Color) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            color = color,
        ),
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DebtCard(
    debt: DebtItem,
    index: Int,
    ownerColor: Color,
    onSettle: () -> Unit,
    onReceive: (DebtPaymentItem) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val partner = (debt.counterpartName ?: "Partner").ellipsize(PARTNER_NAME_DISPLAY_MAX)
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(LeafShapes.IconSquircle).background(ownerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    HeartBullet(Color.White, sizeDp = 16)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (debt.iAmBorrower) "You owe $partner" else "$partner owes you",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    debt.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete debt", tint = colors.textSecondary)
                }
            }

            if (debt.isSettled) {
                Text(
                    text = "Settled · ${money(debt.original)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.semantic.income,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                HeartTippedProgress(progress = debt.fraction, fillColor = ownerColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${money(debt.remaining)} of ${money(debt.original)} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    // Only the borrower settles — they spend from their own account (ADR-0019 #14).
                    if (debt.iAmBorrower) {
                        TextButton(onClick = onSettle) { Text("Settle") }
                    }
                }
            }

            debt.payments.forEach { payment ->
                PaymentRow(
                    payment = payment,
                    // The lender (not borrower) is the receiver who can add the income leg.
                    canReceive = !debt.iAmBorrower &&
                        payment.payorTxnId != null && payment.receiverTxnId == null,
                    onReceive = { onReceive(payment) },
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(
    payment: DebtPaymentItem,
    canReceive: Boolean,
    onReceive: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = payment.note ?: "Payment",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${money(payment.amount)} · ${formatShortDate(payment.date)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
    when {
        canReceive -> TextButton(onClick = onReceive) { Text("Add to my account") }
        payment.receiverTxnId != null -> Text(
            text = "Added to your account",
            style = MaterialTheme.typography.bodySmall,
            color = colors.semantic.income,
        )
    }
}

@Composable
private fun AddDebtDialog(
    editor: DebtDialog.AddDebt,
    partnerName: String,
    onDirectionChange: (DebtDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val shortPartnerName = partnerName.ellipsize(PARTNER_NAME_DISPLAY_MAX)
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayfulChip(
                        label = "I owe $shortPartnerName",
                        selected = editor.direction == DebtDirection.I_OWE,
                        onClick = { onDirectionChange(DebtDirection.I_OWE) },
                    )
                    PlayfulChip(
                        label = "$shortPartnerName owes me",
                        selected = editor.direction == DebtDirection.THEY_OWE,
                        onClick = { onDirectionChange(DebtDirection.THEY_OWE) },
                    )
                }
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (${currencyGlyph()})") },
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

/**
 * The settle sheet, with the overpay allocation folded in by progressive disclosure
 * (ADR-0055 #2): a normal single-debt payment looks exactly as it always did, and the other
 * "I owe" debts only appear once the typed amount spills past the tapped debt's remaining.
 * Ticking a debt raises the ceiling and puts it next in the fill order.
 */
@Composable
private fun SettleDialog(
    editor: DebtDialog.Settle,
    accounts: List<AccountOption>,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleTarget: (String) -> Unit,
    onPayFull: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val showAllocation = editor.isOverflowing && editor.others.isNotEmpty()
    val allocated = editor.allocations.associate { it.debtId to it.amount }
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text("Settle debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${money(editor.remaining)} left on ${editor.debtLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (${currencyGlyph()})") },
                    singleLine = true,
                    isError = editor.amountError || editor.exceedsCeiling,
                    supportingText = settleAmountHint(editor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showAllocation) {
                    SettleAllocationSection(
                        editor = editor,
                        allocated = allocated,
                        onToggleTarget = onToggleTarget,
                        onPayFull = onPayFull,
                    )
                }
                AccountPicker(
                    accounts = accounts,
                    selectedId = editor.accountId,
                    isError = editor.accountError,
                    onSelect = onAccountChange,
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
        confirmButton = { TextButton(onClick = onSave) { Text("Pay") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * The amount field's supporting line. Over the ceiling it names the shortfall and what is
 * currently payable rather than capping the entry silently; while it fits, it just states the
 * ceiling so ticking another debt visibly buys headroom.
 */
private fun settleAmountHint(editor: DebtDialog.Settle): (@Composable () -> Unit)? = when {
    // Only point at the tick list when there is actually something left to tick — with no
    // other "I owe" debts the ceiling is just this debt, and the plain limit reads better.
    editor.exceedsCeiling && editor.tickedIds.size < editor.others.size -> {
        { Text("Tick more debts to cover this — you can pay up to ${money(editor.ceiling)}") }
    }

    editor.exceedsCeiling || editor.amountError -> {
        { Text("Enter an amount up to ${money(editor.ceiling)}") }
    }

    editor.isOverflowing -> {
        { Text("Covers ${editor.allocations.size} debts") }
    }

    else -> null
}

@Composable
private fun SettleAllocationSection(
    editor: DebtDialog.Settle,
    allocated: Map<String, BigDecimal>,
    onToggleTarget: (String) -> Unit,
    onPayFull: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "That's more than this debt. Pick which of your other debts it should cover — " +
                "they're paid in the order you tick them.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        editor.others.forEach { candidate ->
            val ticked = candidate.debtId in editor.tickedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleTarget(candidate.debtId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ticked, onCheckedChange = { onToggleTarget(candidate.debtId) })
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val share = allocated[candidate.debtId]
                Text(
                    text = if (share != null) "${money(share)} of ${money(candidate.remaining)}" else money(candidate.remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (share != null) colors.semantic.income else colors.textSecondary,
                )
            }
        }
        TextButton(onClick = onPayFull) { Text("Pay everything I owe") }
    }
}

@Composable
private fun ReceiveDialog(
    editor: DebtDialog.Receive,
    accounts: List<AccountOption>,
    onAccountChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text("Add to my account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Record ${money(editor.amount)} received for ${editor.debtLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccountPicker(
                    accounts = accounts,
                    selectedId = editor.accountId,
                    isError = editor.accountError,
                    onSelect = onAccountChange,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun AccountPicker(
    accounts: List<AccountOption>,
    selectedId: String?,
    isError: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (accounts.isEmpty()) {
            Text(
                text = "Add an account first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    IponFilterChip(
                        selected = account.id == selectedId,
                        onClick = { onSelect(account.id) },
                        label = { Text(account.name) },
                    )
                }
            }
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
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** A member's stored accent if usable, else a Playful accent/deepPlum fallback distinct per side. */
@Composable
private fun ownerColor(accentHex: String?, isMine: Boolean): Color {
    val colors = LocalPlayfulColors.current
    return parseHexColor(accentHex) ?: if (isMine) colors.accent else colors.deepPlum
}
