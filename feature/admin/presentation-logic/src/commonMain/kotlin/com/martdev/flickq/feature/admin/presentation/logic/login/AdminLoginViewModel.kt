package com.martdev.flickq.feature.admin.presentation.logic.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository
import com.martdev.flickq.validation.Validator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminLoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface AdminLoginAction {
    data class OnEmailChange(val email: String) : AdminLoginAction
    data class OnPasswordChange(val password: String) : AdminLoginAction
    data object OnSubmit : AdminLoginAction
}

sealed interface AdminLoginEvent {
    data object Authenticated : AdminLoginEvent
}

/**
 * Admin login reuses the customer `/authentication/login` endpoint, then decodes the JWT
 * `role` claim to gate entry. Role gating here is cosmetic — the server's `withRole(ADMIN)`
 * is the real guard — so a non-admin who logs in is signed straight back out and told they
 * lack access, rather than being shown admin screens that would 403 anyway.
 */
class AdminLoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminLoginState())
    val state = _state.asStateFlow()

    private val _events = Channel<AdminLoginEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: AdminLoginAction) {
        when (action) {
            is AdminLoginAction.OnEmailChange ->
                _state.update { it.copy(email = action.email, error = null) }
            is AdminLoginAction.OnPasswordChange ->
                _state.update { it.copy(password = action.password, error = null) }
            AdminLoginAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        val emailValidation = Validator.validateEmail(current.email)
        val passwordValidation = Validator.validatePassword(current.password)
        when {
            !emailValidation.isValid -> {
                _state.update {
                    it.copy(
                        error = UiText.DynamicString(emailValidation.errorMessage ?: "Invalid email")
                    )
                }
                return
            }
            !passwordValidation.isValid -> {
                _state.update {
                    it.copy(
                        error = UiText.DynamicString(passwordValidation.errorMessage ?: "Invalid password")
                    )
                }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.newLogin(Credentials(current.email.trim(), current.password), true)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AdminLoginEvent.Authenticated)
                }
                .onFailure { error, message ->
                    _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }

    private fun AuthError.toUiText(): UiText = UiText.DynamicString(
        when (this) {
            AuthError.INVALID_CREDENTIALS -> "Incorrect email or password."
            AuthError.EMAIL_ALREADY_REGISTERED -> "This email is already registered."
            AuthError.INVALID_OTP -> "Invalid verification code."
            AuthError.UNKNOWN -> "Something went wrong. Please try again."
            AuthError.NO_INTERNET -> "No internet connection. Check your network and try again."
            else -> ""
        }
    )
}
