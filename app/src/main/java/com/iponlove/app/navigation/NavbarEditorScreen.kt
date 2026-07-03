package com.iponlove.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder + replace editor for the bottom bar (ADR-0017). Holds a working [NavConfig]
 * in local state, mutated synchronously through [NavConfig]'s invariant-keeping methods (always
 * exactly [NavRegistry.MAX_PINS] pins, no dupes) so dragging is smooth and never races the async
 * DataStore write; each change is also persisted via [onApply]. Reachable from the More sheet and
 * from Personalize.
 *
 * The bar always shows exactly [NavRegistry.MAX_PINS] pins (ADR-0017 addendum 2026-07-03): pins
 * can't be removed, only reordered or *replaced*. Tapping a module under "More modules" opens a
 * small picker asking which of the current pins to swap it in for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavbarEditorScreen(
    initialConfig: NavConfig,
    isPaired: Boolean,
    onApply: (NavConfig) -> Unit,
    onBack: () -> Unit,
) {
    var config by remember { mutableStateOf(initialConfig) }
    fun apply(new: NavConfig) {
        config = new
        onApply(new)
    }

    // The incoming module the user tapped to pin — non-null shows the "replace which pin?" picker.
    var replaceTarget by remember { mutableStateOf<NavDestination?>(null) }

    val pinned = config.pinnedIds.mapNotNull { NavRegistry.byId[it] }
    val available = NavRegistry.all.filter {
        it.pinnable && it.id !in config.pinnedIds && (!it.requiresPaired || isPaired)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit navbar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Your bottom bar always shows ${NavRegistry.MAX_PINS} shortcuts. Long-press the " +
                    "handle to reorder — the first item is your home screen. To change which " +
                    "shortcuts appear, tap a module below and pick which pin to replace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text("On the bar", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            ReorderablePins(
                pinned = pinned,
                isPaired = isPaired,
                onMove = { from, to -> apply(config.move(from, to)) },
            )

            Spacer(Modifier.height(28.dp))
            Text("More modules", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (available.isEmpty()) {
                Text(
                    "Everything's on your bar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                available.forEach { dest ->
                    AvailableRow(dest = dest, onReplace = { replaceTarget = dest })
                }
            }
        }
    }

    replaceTarget?.let { incoming ->
        ReplacePinDialog(
            incoming = incoming,
            currentPins = pinned,
            onPick = { outgoingId ->
                apply(config.replace(outgoingId, incoming.id))
                replaceTarget = null
            },
            onDismiss = { replaceTarget = null },
        )
    }
}

@Composable
private fun ReorderablePins(
    pinned: List<NavDestination>,
    isPaired: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val rowHeight = 60.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    // Read the latest order from inside the long-lived drag closure.
    val currentOrder by rememberUpdatedState(pinned.map { it.id })

    Column {
        pinned.forEachIndexed { index, dest ->
            key(dest.id) {
                val dragging = draggingId == dest.id
                Surface(
                    tonalElevation = if (dragging) 8.dp else 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(vertical = 2.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragAccum else 0f },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Reorder ${dest.label}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(8.dp)
                                .pointerInput(dest.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingId = dest.id; dragAccum = 0f },
                                        onDragEnd = { draggingId = null; dragAccum = 0f },
                                        onDragCancel = { draggingId = null; dragAccum = 0f },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            dragAccum += drag.y
                                            val from = currentOrder.indexOf(dest.id)
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            when {
                                                dragAccum > rowHeightPx / 2 && from < currentOrder.lastIndex -> {
                                                    onMove(from, from + 1)
                                                    dragAccum -= rowHeightPx
                                                }
                                                dragAccum < -rowHeightPx / 2 && from > 0 -> {
                                                    onMove(from, from - 1)
                                                    dragAccum += rowHeightPx
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                        Icon(dest.icon, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dest.label, style = MaterialTheme.typography.bodyLarge)
                            val tag = when {
                                index == 0 -> "Home"
                                dest.requiresPaired && !isPaired -> "Paired only"
                                else -> null
                            }
                            if (tag != null) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableRow(dest: NavDestination, onReplace: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onReplace)
            .padding(horizontal = 8.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Icon(dest.icon, contentDescription = null)
        }
        Text(dest.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onReplace) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Replace a pin with ${dest.label}")
        }
    }
}

/**
 * "Replace which pin?" picker. Since the bar count is fixed, pinning [incoming] means swapping it
 * in for one of the current pins — the user chooses which via [NavConfig.replace].
 */
@Composable
private fun ReplacePinDialog(
    incoming: NavDestination,
    currentPins: List<NavDestination>,
    onPick: (outgoingId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace which pin?") },
        text = {
            Column {
                Text(
                    "Pick a shortcut to swap out for ${incoming.label}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                currentPins.forEach { pin ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onPick(pin.id) },
                    ) {
                        Icon(pin.icon, contentDescription = null)
                        Text(pin.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
