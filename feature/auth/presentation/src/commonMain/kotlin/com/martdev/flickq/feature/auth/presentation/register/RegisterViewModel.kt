package com.martdev.flickq.feature.auth.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.auth.domain.AuthRepository
import com.martdev.flickq.feature.auth.presentation.isValidEmail
import com.martdev.flickq.feature.auth.presentation.isValidPassword
import com.martdev.flickq.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiText? = null
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface RegisterAction {
    data class OnEmailChange(val email: String) : RegisterAction
    data class OnPasswordChange(val password: String) : RegisterAction
    data object OnRegisterClick : RegisterAction
    data object OnLoginClick : RegisterAction
}

sealed interface RegisterEvent {
    data class NavigateToOtp(val emailId: String, val registrationToken: String) : RegisterEvent
    data object NavigateToLogin : RegisterEvent
}

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterState())
    val state = _state.asStateFlow()

    private val _events = Channel<RegisterEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.OnEmailChange ->
                _state.update { it.copy(email = action.email, emailError = false, error = null) }

            is RegisterAction.OnPasswordChange ->
                _state.update { it.copy(password = action.password, passwordError = false, error = null) }

            RegisterAction.OnRegisterClick -> register()

            RegisterAction.OnLoginClick -> viewModelScope.launch {
                _events.send(RegisterEvent.NavigateToLogin)
            }
        }
    }

    private fun register() {
        val current = _state.value
        val emailValid = isValidEmail(current.email)
        val passwordValid = isValidPassword(current.password)
        if (!emailValid || !passwordValid) {
            _state.update { it.copy(emailError = !emailValid, passwordError = !passwordValid) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.register(Credentials(current.email, current.password))
                .onSuccess { result ->
                    _state.update { it.copy(isLoading = false) }
                    _events.send(
                        RegisterEvent.NavigateToOtp(
                            emailId = result.emailId,
                            registrationToken = result.registrationToken
                        )
                    )
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }
}
