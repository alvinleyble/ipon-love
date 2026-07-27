package com.iponlove.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.notifications.domain.usecase.ClearAllNotificationsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.DismissNotificationUseCase
import com.iponlove.app.feature.notifications.domain.usecase.MarkAllNotificationsReadUseCase
import com.iponlove.app.feature.notifications.domain.usecase.ObserveNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationInboxViewModel @Inject constructor(
    observeNotifications: ObserveNotificationsUseCase,
    private val markAllRead: MarkAllNotificationsReadUseCase,
    private val dismissNotification: DismissNotificationUseCase,
    private val clearAllNotifications: ClearAllNotificationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationInboxUiState())
    val uiState: StateFlow<NotificationInboxUiState> = _uiState

    init {
        // Snapshot which rows were unread, THEN clear the badge — order matters, otherwise the
        // mark-all lands first and the visit renders with nothing highlighted.
        viewModelScope.launch {
            val unread = observeNotifications().first().filterNot { it.isRead }.map { it.id }.toSet()
            _uiState.update { it.copy(unreadOnEntry = unread) }
            markAllRead()
        }
        observeNotifications()
            .onEach { rows -> _uiState.update { it.copy(notifications = rows, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun dismiss(id: String) {
        viewModelScope.launch { dismissNotification(id) }
    }

    fun clearAll() {
        viewModelScope.launch { clearAllNotifications() }
    }
}
