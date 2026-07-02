package com.iponlove.app.feature.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalculatorOperator(val symbol: String) {
    ADD("+"), SUBTRACT("−"), MULTIPLY("×"), DIVIDE("÷"),
}

data class CalculatorState(
    val display: String = "0",
    val storedValue: BigDecimal? = null,
    val pendingOperator: CalculatorOperator? = null,
    val overwrite: Boolean = true,
    val error: Boolean = false,
)

/**
 * Pure state machine for a standard pocket calculator — sequential left-to-right evaluation
 * (no operator precedence), kept Android-free so button transitions are JVM-testable.
 * BigDecimal throughout (never Double) per the app's money-math convention; divide-by-zero
 * surfaces as an [CalculatorState.error] display state instead of throwing.
 */
object CalculatorEngine {

    private const val MAX_DIGITS = 15
    private val PRECISION = MathContext(15, RoundingMode.HALF_UP)

    fun onDigit(state: CalculatorState, digit: Char): CalculatorState {
        if (state.error) return onDigit(CalculatorState(), digit)
        val current = if (state.overwrite) "" else state.display
        val digitCount = current.count { it.isDigit() }
        if (digitCount >= MAX_DIGITS) return state
        val next = if (current == "0") digit.toString() else current + digit
        return state.copy(display = next, overwrite = false)
    }

    fun onDecimal(state: CalculatorState): CalculatorState {
        if (state.error) return CalculatorState(display = "0.", overwrite = false)
        if (state.overwrite) return state.copy(display = "0.", overwrite = false)
        if (state.display.contains(".")) return state
        return state.copy(display = state.display + ".")
    }

    fun onToggleSign(state: CalculatorState): CalculatorState {
        if (state.error || state.display == "0") return state
        val next = if (state.display.startsWith("-")) state.display.drop(1) else "-${state.display}"
        return state.copy(display = next)
    }

    fun onPercent(state: CalculatorState): CalculatorState {
        if (state.error) return CalculatorState()
        val value = state.display.toBigDecimalOrNull() ?: return state
        val result = value.divide(BigDecimal(100), PRECISION)
        return state.copy(display = format(result), overwrite = true)
    }

    fun onClear(state: CalculatorState): CalculatorState = CalculatorState()

    fun onBackspace(state: CalculatorState): CalculatorState {
        if (state.error) return CalculatorState()
        if (state.overwrite) return state
        val trimmed = state.display.dropLast(1)
        val next = if (trimmed.isEmpty() || trimmed == "-") "0" else trimmed
        return state.copy(display = next, overwrite = next == "0")
    }

    fun onOperator(state: CalculatorState, operator: CalculatorOperator): CalculatorState {
        if (state.error) return state
        val current = state.display.toBigDecimalOrNull() ?: return state
        if (state.storedValue != null && state.pendingOperator != null && !state.overwrite) {
            val result = apply(state.storedValue, current, state.pendingOperator) ?: return errorState()
            return state.copy(
                display = format(result),
                storedValue = result,
                pendingOperator = operator,
                overwrite = true,
            )
        }
        return state.copy(storedValue = current, pendingOperator = operator, overwrite = true)
    }

    fun onEquals(state: CalculatorState): CalculatorState {
        if (state.error) return state
        val stored = state.storedValue ?: return state
        val op = state.pendingOperator ?: return state
        val current = state.display.toBigDecimalOrNull() ?: return state
        val result = apply(stored, current, op) ?: return errorState()
        return CalculatorState(display = format(result), overwrite = true)
    }

    private fun apply(a: BigDecimal, b: BigDecimal, op: CalculatorOperator): BigDecimal? {
        val raw = when (op) {
            CalculatorOperator.ADD -> a + b
            CalculatorOperator.SUBTRACT -> a - b
            CalculatorOperator.MULTIPLY -> a * b
            CalculatorOperator.DIVIDE -> {
                if (b.signum() == 0) return null
                a.divide(b, PRECISION)
            }
        }
        return raw.round(PRECISION)
    }

    private fun errorState(): CalculatorState =
        CalculatorState(display = "Error", error = true, overwrite = true)

    private fun format(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val plain = if (stripped.scale() < 0) stripped.setScale(0) else stripped
        return plain.toPlainString()
    }
}
