package com.iponlove.app.feature.savings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.usecase.CheckSavingsGoalCapUseCase
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
    private val checkGoalCap: CheckSavingsGoalCapUseCase,
    private val analytics: Analytics,
    observePairingState: ObservePairingStateUseCase,
) : ViewModel() {

    private val saved = savedStateHandle
    private val argId: String = savedStateHandle[GOAL_ID_KEY] ?: NEW_GOAL
    private val isNew: Boolean = argId == NEW_GOAL

    private val _uiState = MutableStateFlow(GoalEditorUiState(isNew = isNew))
    val uiState: StateFlow<GoalEditorUiState> = _uiState.asStateFlow()

    init {
        val restored = hydrateFromSaved()
        when {
            restored != null -> _uiState.value = restored
            isNew -> setState { it.copy(loaded = true, goalId = UUID.randomUUID().toString()) }
            else -> viewModelScope.launch {
                val goal = getGoal(argId)
                if (goal == null) {
                    _uiState.update { it.copy(loaded = true, missing = true) }
                } else {
                    setState {
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

    fun onNameChange(value: String) = setState { it.copy(name = value, nameError = false) }
    fun onTargetChange(value: String) =
        setState { it.copy(targetText = value.filter { c -> c.isDigit() || c == '.' }, targetError = false) }
    fun onDateChange(date: LocalDate?) = setState { it.copy(targetDate = date) }
    fun onIconChange(icon: String?) = setState { it.copy(icon = icon) }
    fun onColorChange(color: String?) = setState { it.copy(color = color) }

    fun toggleShared() {
        val s = _uiState.value
        if (s.isPartnerGoal || !s.isPaired) return
        val goalId = s.goalId ?: return
        val turningOn = !s.isShared
        viewModelScope.launch {
            // Gate the shared-goals cap only when turning sharing ON (S7) — un-sharing is never
            // blocked. For a new goal this still guards the local intent, so save() can't slip a
            // shared goal past the cap.
            if (turningOn) {
                when (val check = checkGoalCap(shared = true)) {
                    is CapCheck.Blocked -> {
                        raiseUpsell("shared_savings_goals", "shared goals", check)
                        return@launch
                    }
                    CapCheck.Allowed -> {}
                }
            }
            if (isNew) {
                // Track intent locally; save() applies shareGoal() once the row exists.
                setState { it.copy(isShared = turningOn) }
            } else {
                val coupleId = s.coupleId ?: return@launch
                if (turningOn) shareGoal(goalId, coupleId) else unshareGoal(goalId)
                setState { it.copy(isShared = turningOn) }
            }
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
            // Gate the personal-goals cap at create (S7); Allowed with enforcement off or under cap.
            // A new goal that's being shared is gated by the *shared* cap at toggleShared() instead —
            // it ends up couple-owned, not personal — so only the stays-personal case is checked here.
            if (isNew && !s.isShared) {
                when (val check = checkGoalCap(shared = false)) {
                    is CapCheck.Blocked -> {
                        raiseUpsell("personal_savings_goals", "goals", check)
                        return@launch
                    }
                    CapCheck.Allowed -> {}
                }
            }
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
            clearDraft()
            onDone()
        }
    }

    // upsell is transient UI state — set via _uiState (never setState) so it's not persisted into
    // the SavedStateHandle draft.
    private var upsellSource: String? = null

    private fun raiseUpsell(source: String, entityLabel: String, blocked: CapCheck.Blocked) {
        upsellSource = source
        _uiState.update { it.copy(upsell = UpsellPrompt(entityLabel, blocked.freeLimit, blocked.premiumMax)) }
    }

    fun dismissUpsell() {
        _uiState.update { it.copy(upsell = null) }
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before routing to paywall. */
    fun onUpsellUpgrade(): String {
        val source = upsellSource ?: "personal_savings_goals"
        analytics.log("upsell_tap", source = source)
        _uiState.update { it.copy(upsell = null) }
        return source
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? =
        trim().takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    // --- SavedStateHandle draft persistence (survives process death) ---

    private fun setState(transform: (GoalEditorUiState) -> GoalEditorUiState) {
        _uiState.update(transform)
        val s = _uiState.value
        saved[KEY_GOAL_ID] = s.goalId
        saved[KEY_NAME] = s.name
        saved[KEY_TARGET_TEXT] = s.targetText
        saved[KEY_TARGET_DATE] = s.targetDate?.toEpochDay()
        saved[KEY_ICON] = s.icon
        saved[KEY_COLOR] = s.color
        saved[KEY_IS_SHARED] = s.isShared
        saved[KEY_IS_PARTNER_GOAL] = s.isPartnerGoal
    }

    private fun clearDraft() {
        listOf(
            KEY_GOAL_ID, KEY_NAME, KEY_TARGET_TEXT, KEY_TARGET_DATE,
            KEY_ICON, KEY_COLOR, KEY_IS_SHARED, KEY_IS_PARTNER_GOAL,
        ).forEach { saved.remove<Any>(it) }
    }

    private fun hydrateFromSaved(): GoalEditorUiState? {
        val goalId: String = saved[KEY_GOAL_ID] ?: return null
        return GoalEditorUiState(
            loaded = true,
            isNew = isNew,
            goalId = goalId,
            name = saved[KEY_NAME] ?: "",
            targetText = saved[KEY_TARGET_TEXT] ?: "",
            targetDate = saved.get<Long>(KEY_TARGET_DATE)?.let { LocalDate.ofEpochDay(it) },
            icon = saved[KEY_ICON],
            color = saved[KEY_COLOR],
            isShared = saved[KEY_IS_SHARED] ?: false,
            isPartnerGoal = saved[KEY_IS_PARTNER_GOAL] ?: false,
        )
    }

    companion object {
        const val GOAL_ID_KEY = "goalId"
        const val NEW_GOAL = "new"

        private const val KEY_GOAL_ID = "draft_goal_id"
        private const val KEY_NAME = "draft_name"
        private const val KEY_TARGET_TEXT = "draft_target_text"
        private const val KEY_TARGET_DATE = "draft_target_date"
        private const val KEY_ICON = "draft_icon"
        private const val KEY_COLOR = "draft_color"
        private const val KEY_IS_SHARED = "draft_is_shared"
        private const val KEY_IS_PARTNER_GOAL = "draft_is_partner_goal"
    }
}
