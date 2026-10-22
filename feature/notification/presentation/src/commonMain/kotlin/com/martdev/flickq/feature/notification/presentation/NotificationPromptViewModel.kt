package com.martdev.flickq.feature.notification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.feature.notification.domain.NotificationPromptStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class NotificationPrompt { ENABLE, OPEN_SETTINGS }

data class NotificationPromptState(
    /** `null` hides the card. */
    val prompt: NotificationPrompt? = null,
)

sealed interface NotificationPromptAction {
    data class OnPermissionChecked(val status: NotificationPermissionStatus) : NotificationPromptAction
    data object OnEnableClick : NotificationPromptAction
    data object OnOpenSettingsClick : NotificationPromptAction
    data object OnNotNowClick : NotificationPromptAction
}

sealed interface NotificationPromptEvent {
    data object RequestPermission : NotificationPromptEvent
    data object OpenSettings : NotificationPromptEvent
}

/**
 * The contextual soft prompt shown after a booking. It asks for the system permission only on
 * an explicit tap, turns into "Open settings" once the dialog can no longer be shown, and stays
 * hidden for [SNOOZE] after "Not now", so there is no repeated nagging.
 */
class NotificationPromptViewModel(
    private val store: NotificationPromptStore,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    val state: StateFlow<NotificationPromptState>
        field = MutableStateFlow(NotificationPromptState())

    private val _events = Channel<NotificationPromptEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: NotificationPromptAction) {
        when (action) {
            is NotificationPromptAction.OnPermissionChecked ->
                state.update { it.copy(prompt = promptFor(action.status)) }
            NotificationPromptAction.OnEnableClick -> {
                store.markPermissionRequested()
                viewModelScope.launch { _events.send(NotificationPromptEvent.RequestPermission) }
            }
            NotificationPromptAction.OnOpenSettingsClick ->
                viewModelScope.launch { _events.send(NotificationPromptEvent.OpenSettings) }
            NotificationPromptAction.OnNotNowClick -> {
                store.snoozeUntil(clock.now() + SNOOZE)
                state.update { it.copy(prompt = null) }
            }
        }
    }

    private fun promptFor(status: NotificationPermissionStatus): NotificationPrompt? {
        if (isSnoozed()) return null
        return when (status) {
            NotificationPermissionStatus.GRANTED,
            NotificationPermissionStatus.UNSUPPORTED -> null
            NotificationPermissionStatus.REQUESTABLE -> NotificationPrompt.ENABLE
            NotificationPermissionStatus.UNDETERMINED ->
                if (store.hasRequestedPermission()) NotificationPrompt.OPEN_SETTINGS else NotificationPrompt.ENABLE
            NotificationPermissionStatus.BLOCKED -> NotificationPrompt.OPEN_SETTINGS
        }
    }

    private fun isSnoozed(): Boolean {
        val until = store.snoozedUntil() ?: return false
        val now = clock.now()
        // A snooze further out than SNOOZE means the clock moved back; don't hide forever.
        return now < until && until - now <= SNOOZE
    }

    companion object {
        val SNOOZE: Duration = 14.days
    }
}