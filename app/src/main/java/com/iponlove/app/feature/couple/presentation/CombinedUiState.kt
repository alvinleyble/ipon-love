package com.iponlove.app.feature.couple.presentation

import com.iponlove.app.feature.couple.domain.model.CombinedEntry
import com.iponlove.app.feature.couple.domain.model.MemberSpend

/**
 * Screen state for the combined couple view. [isPaired] is false when the user has no
 * partner (the screen is normally only reachable while paired, but guards the edge).
 */
data class CombinedUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val monthLabel: String = "",
    val members: List<MemberSpend> = emptyList(),
    val entries: List<CombinedEntry> = emptyList(),
)
