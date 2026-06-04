package com.martdev.flickq.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.auth.domain.AuthError
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

data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiText? = null
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface LoginAction {
    data class OnEmailChange(val email: String) : LoginAction
    data class OnPasswordChange(val password: String) : LoginAction
    data object OnLoginClick : LoginAction
    data object OnRegisterClick : LoginAction
}

sealed interface LoginEvent {
    data object NavigateToRegister : LoginEvent
    data object Authenticated : LoginEvent
    data class NavigateToVerify(val email: String, val emailId: String, val token: String) :
        LoginEvent
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange ->
                _state.update { it.copy(email = action.email, emailError = false, error = null) }

            is LoginAction.OnPasswordChange ->
                _state.update {
                    it.copy(
                        password = action.password,
                        passwordError = false,
                        error = null
                    )
                }

            LoginAction.OnLoginClick -> login()

            LoginAction.OnRegisterClick -> viewModelScope.launch {
                _events.send(LoginEvent.NavigateToRegister)
            }
        }
    }

    private fun login() {
        val current = _state.value
        val emailValid = isValidEmail(current.email)
        val passwordValid = isValidPassword(current.password)
        if (!emailValid || !passwordValid) {
            _state.update { it.copy(emailError = !emailValid, passwordError = !passwordValid) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.login(Credentials(current.email, current.password))
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.Authenticated)
                }
                .onFailure { error ->
                    if (error == AuthError.EMAIL_NOT_VERIFIED) {
                        resendOtp(current.email) {
                            return@launch
                        }
                    }
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }

    private suspend inline fun resendOtp(email: String, block: () -> Unit ) {
        val resendOtp = authRepository.resendOtp(email)
        if (resendOtp is Result.Success) {
            _state.update { it.copy(isLoading = false) }
            _events.send(
                LoginEvent.NavigateToVerify(
                    email,
                    resendOtp.data.emailId,
                    resendOtp.data.verificationToken
                )
            )
            block()
        }
    }
}
