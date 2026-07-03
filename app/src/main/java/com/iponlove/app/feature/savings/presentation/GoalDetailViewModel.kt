package com.iponlove.app.feature.savings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.usecase.AddGoalContributionUseCase
import com.iponlove.app.feature.savings.domain.usecase.DeleteGoalContributionUseCase
import com.iponlove.app.feature.savings.domain.usecase.DeleteSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.EditGoalContributionUseCase
import com.iponlove.app.feature.savings.domain.usecase.ObserveGoalContributionsUseCase
import com.iponlove.app.feature.savings.domain.usecase.ObserveSavingsGoalsUseCase
import com.iponlove.app.feature.savings.domain.usecase.SetGoalArchivedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeGoals: ObserveSavingsGoalsUseCase,
    observeContributions: ObserveGoalContributionsUseCase,
    observePairingState: ObservePairingStateUseCase,
    private val addContribution: AddGoalContributionUseCase,
    private val editContribution: EditGoalContributionUseCase,
    private val deleteContributionUseCase: DeleteGoalContributionUseCase,
    private val setArchived: SetGoalArchivedUseCase,
    private val deleteGoalUseCase: DeleteSavingsGoalUseCase,
) : ViewModel() {

    private val goalId: String = savedStateHandle[GoalEditorViewModel.GOAL_ID_KEY] ?: ""

    private val partnerName: StateFlow<String> = observePairingState()
        .map { state -> (state as? PairingState.Paired)?.partner?.displayName ?: "Partner" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Partner")

    private val _uiState = MutableStateFlow(GoalDetailUiState(goalId = goalId))
    val uiState: StateFlow<GoalDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeGoals(),
                observeContributions(goalId),
                partnerName,
            ) { goals, contributions, partner ->
                Triple(goals, contributions, partner)
            }.collect { (goals, contributions, partner) ->
                val progress = goals.firstOrNull { it.goal.id == goalId }
                _uiState.update {
                    if (progress == null) {
                        it.copy(loaded = true, missing = true)
                    } else {
                        val goal = progress.goal
                        it.copy(
                            loaded = true,
                            missing = false,
                            name = goal.name,
                            icon = goal.icon,
                            color = goal.color,
                            targetAmount = goal.targetAmount,
                            savedAmount = progress.savedAmount,
                            progress = progress.progress,
                            reached = progress.reached,
                            targetDate = goal.targetDate,
                            isShared = goal.isShared,
                            isArchived = goal.isArchived,
                            canManage = !goal.isPartnerGoal,
                            contributions = contributions.map { c -> c.toRow(partner) },
                        )
                    }
                }
            }
        }
    }

    fun addContribution(amount: BigDecimal, date: Instant, note: String?) {
        viewModelScope.launch { addContribution.invoke(goalId, amount, date, note) }
    }

    fun editContribution(id: String, amount: BigDecimal, date: Instant, note: String?) {
        viewModelScope.launch { editContribution.invoke(id, amount, date, note) }
    }

    fun deleteContribution(id: String) {
        viewModelScope.launch { deleteContributionUseCase(id) }
    }

    fun toggleArchive() {
        val archived = _uiState.value.isArchived
        viewModelScope.launch { setArchived(goalId, !archived) }
    }

    fun deleteGoal(onDone: () -> Unit) {
        viewModelScope.launch {
            deleteGoalUseCase(goalId)
            onDone()
        }
    }

    private fun GoalContribution.toRow(partner: String): ContributionRow = ContributionRow(
        id = id,
        amount = amount,
        note = note,
        date = date,
        isMine = isMine,
        byLabel = if (isMine) "You" else partner,
    )
}
