package com.iponlove.app.feature.calculator.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.calculator.domain.BubblePlacement
import com.iponlove.app.feature.calculator.domain.CalculatorDisplayFormat
import com.iponlove.app.feature.calculator.domain.CalculatorEngine
import com.iponlove.app.feature.calculator.domain.CalculatorOperator
import com.iponlove.app.feature.calculator.domain.CalculatorState
import com.iponlove.app.feature.calculator.presentation.CalculatorBubbleState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Breathing room from every screen edge the pill snaps to. */
private val EDGE_MARGIN = 8.dp

/**
 * The bottom bar's height (`PlayfulBottomBar`), reserved so a dragged pill can never come to rest
 * on top of the nav bar. A constant rather than a measurement: the bubble draws in the shell's root
 * Box as a sibling of the Scaffold, so it has no access to the bar's bounds — and the reserve should
 * hold even on the routes that have no bar (add/edit transaction), where a pill pinned to the very
 * bottom would sit under the thumb reaching for Save.
 */
private val BOTTOM_BAR_RESERVE = 74.dp

private val PANEL_WIDTH = 288.dp
private val KEY_HEIGHT = 44.dp
private const val COPIED_FLASH_MS = 1_200L

/**
 * The floating calculator (ADR-0058): a draggable, edge-snapping pill carrying the running value,
 * which expands into a compact keypad anchored to whichever side it rests on.
 *
 * Drawn in the shell's root `Box` (below the coach-mark overlay) rather than by any screen, which is
 * what lets it persist across tab switches — read a figure off Records and keep the total in view
 * while navigating to Budgets. Window-owning layers (the app-lock `Dialog`, dialogs, the More sheet)
 * cover it for free, so there is no z-order work here beyond that one ordering.
 *
 * All durable state belongs to [state] up at the shell, so it survives rotation, backgrounding, and
 * an app-lock unlock. This composable owns only the genuinely transient parts: the live drag
 * position and the "Copied" flash.
 */
@Composable
fun CalculatorBubble(
    state: CalculatorBubbleState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Back collapses rather than closes (decision 4) — back is the most reflexively pressed control
    // in the app, and it gets pressed while navigating, exactly when a live scratch value matters.
    BackHandler(enabled = state.expanded) { state.collapse() }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FLASH_MS)
            copied = false
        }
    }
    fun copyValue() {
        // The clipboard gets the engine's raw display, never the grouped pill text — the point is
        // pasting into an amount field, and "1,590" doesn't parse as money.
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Calculator", state.value.display))
        copied = true
    }

    val topInsetPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val navInsetPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val marginPx = with(density) { EDGE_MARGIN.toPx() }
    val barReservePx = with(density) { BOTTOM_BAR_RESERVE.toPx() } + navInsetPx

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val trackTop = topInsetPx + marginPx

        if (state.expanded) {
            var panelSize by remember { mutableStateOf(IntSize.Zero) }
            val panelX =
                if (state.slot.onRightEdge) {
                    (containerW - panelSize.width - marginPx).coerceAtLeast(marginPx)
                } else {
                    marginPx
                }
            // The panel doesn't drag; it anchors to the pill's resting height. Its own height is
            // subtracted from the track so a tall panel opening off a low pill still fits above the bar.
            val panelY = trackTop + BubblePlacement.yPx(
                slot = state.slot,
                trackHeight = (containerH - trackTop - barReservePx - panelSize.height).coerceAtLeast(0f),
            )
            CalculatorPanel(
                value = state.value,
                copied = copied,
                onKey = { transform -> state.value = transform(state.value) },
                onCopy = { copyValue() },
                onCollapse = state::collapse,
                onClose = state::close,
                modifier = Modifier
                    .onSizeChanged { panelSize = it }
                    .offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }
                    .alpha(if (panelSize == IntSize.Zero) 0f else 1f),
            )
            return@BoxWithConstraints
        }

        // ── Collapsed pill ────────────────────────────────────────────────────────────────────
        var pillSize by remember { mutableStateOf(IntSize.Zero) }
        val trackHeight = (containerH - trackTop - barReservePx - pillSize.height).coerceAtLeast(0f)
        val restX =
            if (state.slot.onRightEdge) {
                (containerW - pillSize.width - marginPx).coerceAtLeast(marginPx)
            } else {
                marginPx
            }
        val restY = trackTop + BubblePlacement.yPx(state.slot, trackHeight)

        val x = remember { Animatable(0f) }
        val y = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        // Bumped on every release so the glide home runs even when the drag ended in the slot it
        // started from (same slot ⇒ restX/restY unchanged ⇒ nothing else would re-trigger it).
        var releases by remember { mutableIntStateOf(0) }

        // `pillSize` is a key, not just a guard, and that is load-bearing (on-device find): the
        // first pass always runs against an unmeasured pill and bails out below, so the effect has
        // to be re-triggered by the measurement itself. Keying only on restX/restY looks equivalent
        // but isn't — on the LEFT edge restX is a constant `margin`, and at yFraction 0 restY is a
        // constant `trackTop`, so nothing would ever change, `placed` would stay false, and the
        // pill would render at alpha 0 forever. That reads on device as "the bubble vanished",
        // and it struck precisely after an Activity recreation restored a left-parked slot.
        LaunchedEffect(restX, restY, releases, pillSize) {
            // Wait for the first real measurement: until then restX is computed against a zero-width
            // pill, which would place it off the right edge.
            if (pillSize == IntSize.Zero) return@LaunchedEffect
            if (!placed) {
                x.snapTo(restX)
                y.snapTo(restY)
                placed = true
                return@LaunchedEffect
            }
            if (dragging) return@LaunchedEffect
            val glide = spring<Float>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            )
            launch { x.animateTo(restX, glide) }
            launch { y.animateTo(restY, glide) }
        }

        CalculatorPill(
            label = if (copied) "Copied" else CalculatorDisplayFormat.grouped(state.value.display),
            error = state.value.error && !copied,
            modifier = Modifier
                .onSizeChanged { pillSize = it }
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .alpha(if (placed) 1f else 0f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragCancel = {
                            dragging = false
                            releases++
                        },
                        onDragEnd = {
                            dragging = false
                            state.slot = BubblePlacement.snap(
                                centerX = x.value + pillSize.width / 2f,
                                y = y.value - trackTop,
                                trackWidth = containerW,
                                trackHeight = trackHeight,
                            )
                            releases++
                        },
                    ) { _, dragAmount ->
                        scope.launch {
                            x.snapTo(x.value + dragAmount.x)
                            y.snapTo(y.value + dragAmount.y)
                        }
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Expand calculator",
                    onLongClickLabel = "Copy value",
                    onClick = state::expand,
                    onLongClick = { copyValue() },
                ),
        )
    }
}

/** The collapsed pill: the running value, readable without expanding anything (decision 3). */
@Composable
private fun CalculatorPill(
    label: String,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = if (error) colors.semantic.negative else colors.accent,
        contentColor = colors.onAccent,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Calculate, contentDescription = "Calculator", Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

/** The expanded compact keypad, anchored to the pill's side. Does not drag (decision 8). */
@Composable
private fun CalculatorPanel(
    value: CalculatorState,
    copied: Boolean,
    onKey: ((CalculatorState) -> CalculatorState) -> Unit,
    onCopy: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    Surface(
        modifier = modifier.width(PANEL_WIDTH),
        shape = MaterialTheme.shapes.large,
        color = colors.navSurface,
        contentColor = colors.textPrimary,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (copied) "Copied" else "Calculator",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy value", Modifier.size(18.dp))
                }
                IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", Modifier.size(20.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close calculator", Modifier.size(18.dp))
                }
            }

            // The pending half of the expression ("120 +"), so an interrupted calculation reads back.
            val expression = value.storedValue?.let { stored ->
                value.pendingOperator?.let { op -> "${stored.toPlainString()} ${op.symbol}" }
            }
            Text(
                text = expression.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = CalculatorDisplayFormat.grouped(value.display),
                style = MaterialTheme.typography.headlineSmall,
                color = if (value.error) colors.semantic.negative else colors.textPrimary,
                textAlign = TextAlign.End,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            val gap = Arrangement.spacedBy(6.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                CalcKey("C", functionColors()) { onKey(CalculatorEngine::onClear) }
                CalcKey("⌫", functionColors()) { onKey(CalculatorEngine::onBackspace) }
                CalcKey("%", functionColors()) { onKey(CalculatorEngine::onPercent) }
                CalcKey("÷", operatorColors()) { onKey { CalculatorEngine.onOperator(it, CalculatorOperator.DIVIDE) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                DigitKey('7', onKey)
                DigitKey('8', onKey)
                DigitKey('9', onKey)
                CalcKey("×", operatorColors()) { onKey { CalculatorEngine.onOperator(it, CalculatorOperator.MULTIPLY) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                DigitKey('4', onKey)
                DigitKey('5', onKey)
                DigitKey('6', onKey)
                CalcKey("−", operatorColors()) { onKey { CalculatorEngine.onOperator(it, CalculatorOperator.SUBTRACT) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                DigitKey('1', onKey)
                DigitKey('2', onKey)
                DigitKey('3', onKey)
                CalcKey("+", operatorColors()) { onKey { CalculatorEngine.onOperator(it, CalculatorOperator.ADD) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                CalcKey("±") { onKey(CalculatorEngine::onToggleSign) }
                DigitKey('0', onKey)
                CalcKey(".") { onKey(CalculatorEngine::onDecimal) }
                CalcKey("=", operatorColors()) { onKey(CalculatorEngine::onEquals) }
            }
        }
    }
}

@Composable
private fun RowScope.DigitKey(digit: Char, onKey: ((CalculatorState) -> CalculatorState) -> Unit) {
    CalcKey(digit.toString()) { onKey { CalculatorEngine.onDigit(it, digit) } }
}

/** One keypad key — same engine calls as the retired full screen, at bubble scale. */
@Composable
private fun RowScope.CalcKey(
    label: String,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = colors,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .weight(1f)
            .height(KEY_HEIGHT),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
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
