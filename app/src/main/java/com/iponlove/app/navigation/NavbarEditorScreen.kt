package com.iponlove.app.navigation

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder + pin/unpin editor for the bottom bar (ADR-0017). Holds a working [NavConfig]
 * in local state, mutated synchronously through [NavConfig]'s invariant-keeping methods (min 1 /
 * max 4 / no dupes) so dragging is smooth and never races the async DataStore write; each change
 * is also persisted via [onApply]. Reachable from the More sheet and from Personalize.
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

    val pinned = config.pinnedIds.mapNotNull { NavRegistry.byId[it] }
    val available = NavRegistry.all.filter {
        it.id !in config.pinnedIds && (!it.requiresPaired || isPaired)
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
                "Pick up to ${NavRegistry.MAX_PINS} shortcuts for your bottom bar. " +
                    "Long-press the handle to reorder — the first item is your home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text("On the bar", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            ReorderablePins(
                pinned = pinned,
                isPaired = isPaired,
                canUnpin = config.pinnedIds.size > 1,
                onMove = { from, to -> apply(config.move(from, to)) },
                onUnpin = { id -> apply(config.unpin(id)) },
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
                val canPin = config.pinnedIds.size < NavRegistry.MAX_PINS
                available.forEach { dest ->
                    AvailableRow(dest = dest, canPin = canPin, onPin = { apply(config.pin(dest.id)) })
                }
            }
        }
    }
}

@Composable
private fun ReorderablePins(
    pinned: List<NavDestination>,
    isPaired: Boolean,
    canUnpin: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onUnpin: (String) -> Unit,
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
                        IconButton(onClick = { onUnpin(dest.id) }, enabled = canUnpin) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove ${dest.label}",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableRow(dest: NavDestination, canPin: Boolean, onPin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp)
            .alpha(if (canPin) 1f else 0.45f),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Icon(dest.icon, contentDescription = null)
        }
        Text(dest.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onPin, enabled = canPin) {
            Icon(Icons.Filled.Add, contentDescription = "Pin ${dest.label}")
        }
    }
}
