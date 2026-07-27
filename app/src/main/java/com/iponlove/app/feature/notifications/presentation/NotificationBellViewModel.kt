package com.iponlove.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.notifications.domain.usecase.ObserveUnreadNotificationCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the bell's unread badge. Held once at the nav shell (not per screen) so switching tabs
 * never re-subscribes or flashes a stale count.
 */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    observeUnreadCount: ObserveUnreadNotificationCountUseCase,
) : ViewModel() {

    val unreadCount: StateFlow<Int> = observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
