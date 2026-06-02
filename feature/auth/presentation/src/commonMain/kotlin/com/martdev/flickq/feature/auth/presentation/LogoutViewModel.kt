package com.martdev.flickq.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.feature.auth.domain.AuthRepository
import kotlinx.coroutines.launch

/**
 * Logs the user out: revokes the session via [AuthRepository.logout] (best-effort server revoke +
 * local token clear) then signals [SessionManager] so the root nav routes back to login — the same
 * path session-expiry takes, so no extra navigation wiring is needed.
 */
class LogoutViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            sessionManager.notifyLoggedOut()
        }
    }
}
