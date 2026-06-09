package com.martdev.flickq.feature.admin.presentation.logic.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.feature.auth.domain.AuthRepository
import kotlinx.coroutines.launch

/**
 * Backs the admin hub's "Log out" action: revokes the session via [AuthRepository.logout] then
 * signals [SessionManager] so the admin app's root nav routes back to the admin login (same path
 * as session expiry).
 */
class AdminHubViewModel(
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
