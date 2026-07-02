package com.iponlove.app.feature.calculator

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.calculator.domain.CalculatorEngine
import com.iponlove.app.feature.calculator.domain.CalculatorOperator
import com.iponlove.app.feature.calculator.domain.CalculatorState
import org.junit.Test

class CalculatorEngineTest {

    private fun digits(state: CalculatorState, text: String): CalculatorState =
        text.fold(state) { acc, c -> if (c == '.') CalculatorEngine.onDecimal(acc) else CalculatorEngine.onDigit(acc, c) }

    @Test
    fun onDigit_firstPress_replacesLeadingZero() {
        val result = CalculatorEngine.onDigit(CalculatorState(), '5')
        assertThat(result.display).isEqualTo("5")
        assertThat(result.overwrite).isFalse()
    }

    @Test
    fun onDigit_appendsToExistingDisplay() {
        val result = digits(CalculatorState(), "12")
        assertThat(result.display).isEqualTo("12")
    }

    @Test
    fun onDecimal_addsPointOnce() {
        val once = CalculatorEngine.onDecimal(digits(CalculatorState(), "3"))
        val twice = CalculatorEngine.onDecimal(once)
        assertThat(once.display).isEqualTo("3.")
        assertThat(twice.display).isEqualTo("3.")
    }

    @Test
    fun onDecimal_afterOverwrite_startsWithZeroPoint() {
        val result = CalculatorEngine.onDecimal(CalculatorState(overwrite = true))
        assertThat(result.display).isEqualTo("0.")
    }

    @Test
    fun addition_simple() {
        var state = digits(CalculatorState(), "12")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = digits(state, "5")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("17")
    }

    @Test
    fun subtraction_resultCanGoNegative() {
        var state = digits(CalculatorState(), "3")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.SUBTRACT)
        state = digits(state, "10")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("-7")
    }

    @Test
    fun multiplication_decimal() {
        var state = digits(CalculatorState(), "2.5")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.MULTIPLY)
        state = digits(state, "4")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("10")
    }

    @Test
    fun division_nonTerminating_roundsToPrecision() {
        var state = digits(CalculatorState(), "10")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.DIVIDE)
        state = digits(state, "3")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).startsWith("3.333333333333")
    }

    @Test
    fun division_byZero_entersErrorState() {
        var state = digits(CalculatorState(), "5")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.DIVIDE)
        state = digits(state, "0")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.error).isTrue()
        assertThat(state.display).isEqualTo("Error")
    }

    @Test
    fun onDigit_afterError_startsFreshEntry() {
        val error = CalculatorState(display = "Error", error = true, overwrite = true)
        val result = CalculatorEngine.onDigit(error, '7')
        assertThat(result.error).isFalse()
        assertThat(result.display).isEqualTo("7")
    }

    @Test
    fun chainedOperators_evaluatesPendingBeforeSwitching() {
        var state = digits(CalculatorState(), "5")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = digits(state, "3")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.MULTIPLY)
        assertThat(state.display).isEqualTo("8")
        assertThat(state.pendingOperator).isEqualTo(CalculatorOperator.MULTIPLY)
        state = digits(state, "2")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("16")
    }

    @Test
    fun onOperator_pressedTwiceInARow_justSwapsOperator() {
        var state = digits(CalculatorState(), "5")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = CalculatorEngine.onOperator(state, CalculatorOperator.SUBTRACT)
        assertThat(state.pendingOperator).isEqualTo(CalculatorOperator.SUBTRACT)
        assertThat(state.storedValue?.toPlainString()).isEqualTo("5")
        assertThat(state.display).isEqualTo("5")
    }

    @Test
    fun onEquals_continuingFromPreviousResult_startsNewChain() {
        var state = digits(CalculatorState(), "2")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = digits(state, "3")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("5")

        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = digits(state, "10")
        state = CalculatorEngine.onEquals(state)
        assertThat(state.display).isEqualTo("15")
    }

    @Test
    fun onToggleSign_flipsAndUnflips() {
        val state = digits(CalculatorState(), "9")
        val negated = CalculatorEngine.onToggleSign(state)
        assertThat(negated.display).isEqualTo("-9")
        val positive = CalculatorEngine.onToggleSign(negated)
        assertThat(positive.display).isEqualTo("9")
    }

    @Test
    fun onToggleSign_zero_isNoOp() {
        val result = CalculatorEngine.onToggleSign(CalculatorState())
        assertThat(result.display).isEqualTo("0")
    }

    @Test
    fun onPercent_convertsToHundredth() {
        val state = digits(CalculatorState(), "50")
        val result = CalculatorEngine.onPercent(state)
        assertThat(result.display).isEqualTo("0.5")
        assertThat(result.overwrite).isTrue()
    }

    @Test
    fun onBackspace_removesLastDigit() {
        val state = digits(CalculatorState(), "123")
        val result = CalculatorEngine.onBackspace(state)
        assertThat(result.display).isEqualTo("12")
    }

    @Test
    fun onBackspace_lastDigit_returnsToZero() {
        val state = digits(CalculatorState(), "7")
        val result = CalculatorEngine.onBackspace(state)
        assertThat(result.display).isEqualTo("0")
        assertThat(result.overwrite).isTrue()
    }

    @Test
    fun onBackspace_whileOverwrite_isNoOp() {
        val state = CalculatorState(display = "8", overwrite = true)
        val result = CalculatorEngine.onBackspace(state)
        assertThat(result.display).isEqualTo("8")
    }

    @Test
    fun onClear_resetsToInitialState() {
        var state = digits(CalculatorState(), "42")
        state = CalculatorEngine.onOperator(state, CalculatorOperator.ADD)
        state = CalculatorEngine.onClear(state)
        assertThat(state).isEqualTo(CalculatorState())
    }

    @Test
    fun onDigit_capsAtMaxDigits() {
        val state = digits(CalculatorState(), "1234567890123456789")
        assertThat(state.display.count { it.isDigit() }).isEqualTo(15)
    }
}
