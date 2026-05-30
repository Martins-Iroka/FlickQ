package com.martdev.flickq.feature.auth.presentation.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.auth.domain.AuthRepository
import com.martdev.flickq.feature.auth.presentation.OTP_LENGTH
import com.martdev.flickq.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtpVerifyState(
    val email: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val info: UiText? = null,
    val error: UiText? = null
) {
    val canSubmit: Boolean get() = code.length == OTP_LENGTH && !isLoading
}

sealed interface OtpVerifyAction {
    data class OnCodeChange(val code: String) : OtpVerifyAction
    data object OnVerifyClick : OtpVerifyAction
    data object OnResendClick : OtpVerifyAction
}

sealed interface OtpVerifyEvent {
    data object Authenticated : OtpVerifyEvent
}

class OtpVerifyViewModel(
    private val emailId: String,
    registrationToken: String,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var verificationToken: String = registrationToken

    private val _state = MutableStateFlow(OtpVerifyState(email = emailId))
    val state = _state.asStateFlow()

    private val _events = Channel<OtpVerifyEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: OtpVerifyAction) {
        when (action) {
            is OtpVerifyAction.OnCodeChange -> {
                val digits = action.code.filter { it.isDigit() }.take(OTP_LENGTH)
                _state.update { it.copy(code = digits, error = null) }
            }

            OtpVerifyAction.OnVerifyClick -> verify()
            OtpVerifyAction.OnResendClick -> resend()
        }
    }

    private fun verify() {
        val current = _state.value
        if (current.code.length != OTP_LENGTH) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null) }
            authRepository.verifyOtp(
                VerificationInput(
                    code = current.code,
                    emailId = emailId,
                    registrationToken = verificationToken
                )
            )
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(OtpVerifyEvent.Authenticated)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }

    private fun resend() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null) }
            authRepository.resendOtp(emailId)
                .onSuccess { result ->
                    verificationToken = result.verificationToken
                    _state.update {
                        it.copy(
                            isLoading = false,
                            info = UiText.DynamicString("A new code has been sent.")
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }
}
