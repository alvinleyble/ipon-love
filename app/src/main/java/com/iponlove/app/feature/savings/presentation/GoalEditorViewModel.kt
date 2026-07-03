package com.iponlove.app.feature.savings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.usecase.GetSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.ShareSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.UnshareSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.UpsertSavingsGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGoal: GetSavingsGoalUseCase,
    private val upsertGoal: UpsertSavingsGoalUseCase,
    private val shareGoal: ShareSavingsGoalUseCase,
    private val unshareGoal: UnshareSavingsGoalUseCase,
    observePairingState: ObservePairingStateUseCase,
) : ViewModel() {

    private val argId: String = savedStateHandle[GOAL_ID_KEY] ?: NEW_GOAL
    private val isNew: Boolean = argId == NEW_GOAL

    private val _uiState = MutableStateFlow(GoalEditorUiState(isNew = isNew))
    val uiState: StateFlow<GoalEditorUiState> = _uiState.asStateFlow()

    init {
        if (isNew) {
            _uiState.update { it.copy(loaded = true, goalId = UUID.randomUUID().toString()) }
        } else {
            viewModelScope.launch {
                val goal = getGoal(argId)
                _uiState.update {
                    if (goal == null) {
                        it.copy(loaded = true, missing = true)
                    } else {
                        it.copy(
                            loaded = true,
                            goalId = goal.id,
                            name = goal.name,
                            targetText = goal.targetAmount.stripTrailingZeros().toPlainString(),
                            targetDate = goal.targetDate,
                            icon = goal.icon,
                            color = goal.color,
                            isShared = goal.isShared,
                            isPartnerGoal = goal.isPartnerGoal,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            observePairingState().collect { state ->
                val paired = state is PairingState.Paired
                val coupleId = (state as? PairingState.Paired)?.couple?.id
                _uiState.update { it.copy(isPaired = paired, coupleId = coupleId) }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }
    fun onTargetChange(value: String) =
        _uiState.update { it.copy(targetText = value.filter { c -> c.isDigit() || c == '.' }, targetError = false) }
    fun onDateChange(date: LocalDate?) = _uiState.update { it.copy(targetDate = date) }
    fun onIconChange(icon: String?) = _uiState.update { it.copy(icon = icon) }
    fun onColorChange(color: String?) = _uiState.update { it.copy(color = color) }

    fun toggleShared() {
        val s = _uiState.value
        if (s.isPartnerGoal || !s.isPaired) return
        val goalId = s.goalId ?: return
        if (isNew) {
            // Track intent locally; save() applies shareGoal() once the row exists.
            _uiState.update { it.copy(isShared = !it.isShared) }
            return
        }
        val coupleId = s.coupleId ?: return
        val nowShared = s.isShared
        viewModelScope.launch {
            if (nowShared) unshareGoal(goalId) else shareGoal(goalId, coupleId)
            _uiState.update { it.copy(isShared = !nowShared) }
        }
    }

    /** Validate + persist. Calls [onDone] only on success (so the screen can navigate back). */
    fun save(onDone: () -> Unit) {
        val s = _uiState.value
        if (s.isPartnerGoal || s.missing) { onDone(); return }
        val id = s.goalId ?: run { onDone(); return }
        val target = s.targetText.toBigDecimalOrNull()
        val nameBlank = s.name.isBlank()
        val targetInvalid = target == null || target <= BigDecimal.ZERO
        if (nameBlank || targetInvalid) {
            _uiState.update { it.copy(nameError = nameBlank, targetError = targetInvalid) }
            return
        }
        viewModelScope.launch {
            upsertGoal(
                SavingsGoal(
                    id = id,
                    name = s.name.trim(),
                    targetAmount = target,
                    targetDate = s.targetDate,
                    icon = s.icon,
                    color = s.color,
                ),
            )
            if (isNew && s.isShared) {
                s.coupleId?.let { shareGoal(id, it) }
            }
            onDone()
        }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? =
        trim().takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    companion object {
        const val GOAL_ID_KEY = "goalId"
        const val NEW_GOAL = "new"
    }
}
