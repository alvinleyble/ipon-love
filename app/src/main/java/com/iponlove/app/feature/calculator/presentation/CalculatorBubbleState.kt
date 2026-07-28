package com.iponlove.app.feature.calculator.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import com.iponlove.app.feature.calculator.domain.BubbleSlot
import com.iponlove.app.feature.calculator.domain.CalculatorOperator
import com.iponlove.app.feature.calculator.domain.CalculatorState
import java.math.BigDecimal

/**
 * The calculator bubble's whole session state, hoisted to the app shell (ADR-0058 decision 2).
 *
 * Living at the shell rather than inside a screen is what makes the bubble outlive navigation: the
 * user can leave Records for Analysis, rotate, background the app and come back through the app
 * lock, and the running value is still on the pill. It dies only on process death, where
 * `rememberSaveable`'s bundle goes with it — deliberate, since a scratch value outliving a
 * force-stop fights the ephemeral mental model (ADR-0058, rejected alternatives).
 *
 * [close] is the only way the value is cleared, and it always clears: "closed" must never mean
 * "hidden but still holding yesterday's arithmetic". Collapsing is *not* closing ([expanded]).
 */
@Stable
class CalculatorBubbleState(
    open: Boolean = false,
    expanded: Boolean = false,
    slot: BubbleSlot = BubbleSlot(),
    value: CalculatorState = CalculatorState(),
) {
    /** Whether the bubble exists on screen at all (pill or panel). */
    var open by mutableStateOf(open)
        private set

    /** Open *and* showing the keypad panel; false = the collapsed pill. */
    var expanded by mutableStateOf(expanded)
        private set

    /** Where the collapsed pill rests. Survives collapse/expand — the panel anchors to it. */
    var slot by mutableStateOf(slot)

    /** The engine state the keypad drives and the pill displays. */
    var value by mutableStateOf(value)

    /**
     * The nav entry's toggle (ADR-0058 decision 4): tapping Calculator on the bar or in the More
     * sheet opens the bubble expanded and ready to type, or — if it is already up anywhere — closes
     * it. Toggling from the bar guarantees an escape hatch even if the pill has been dragged
     * somewhere awkward.
     */
    fun toggle() {
        if (open) close() else open()
    }

    fun open() {
        open = true
        expanded = true
    }

    /** Collapse to the pill, keeping the value — what back does while expanded. */
    fun collapse() {
        expanded = false
    }

    fun expand() {
        expanded = true
    }

    /** Dismiss and clear. The ✕, and the nav entry's second tap. */
    fun close() {
        open = false
        expanded = false
        value = CalculatorState()
    }

    companion object {
        /**
         * Flattens to bundle-safe primitives. A [CalculatorState] can't go into a `Bundle` as-is
         * (it holds a [BigDecimal] and isn't `Parcelable`), and its `storedValue` is exactly the
         * field a naive saver would silently drop — losing the pending half of `120 +` across a
         * rotation. Stored as a plain string and re-parsed.
         */
        val Saver: Saver<CalculatorBubbleState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.open,
                    state.expanded,
                    state.slot.onRightEdge,
                    state.slot.yFraction,
                    state.value.display,
                    state.value.storedValue?.toPlainString(),
                    state.value.pendingOperator?.name,
                    state.value.overwrite,
                    state.value.error,
                )
            },
            restore = { saved ->
                CalculatorBubbleState(
                    open = saved[0] as Boolean,
                    expanded = saved[1] as Boolean,
                    slot = BubbleSlot(
                        onRightEdge = saved[2] as Boolean,
                        yFraction = saved[3] as Float,
                    ),
                    value = CalculatorState(
                        display = saved[4] as String,
                        storedValue = (saved[5] as String?)?.let { BigDecimal(it) },
                        pendingOperator = (saved[6] as String?)?.let { CalculatorOperator.valueOf(it) },
                        overwrite = saved[7] as Boolean,
                        error = saved[8] as Boolean,
                    ),
                )
            },
        )
    }
}

@Composable
fun rememberCalculatorBubbleState(): CalculatorBubbleState =
    rememberSaveable(saver = CalculatorBubbleState.Saver) { CalculatorBubbleState() }
