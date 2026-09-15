package com.martdev.flickq.feature.auth.presentation.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.feature.auth.domain.AuthRepository
import com.martdev.flickq.feature.auth.presentation.OTP_LENGTH
import com.martdev.flickq.feature.auth.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtpVerifyState(
    val email: String = "",
    val emailId: String = "",
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
    /** The account is verified; the user must now log in (verification issues no session). */
    data object Verified : OtpVerifyEvent
}

class OtpVerifyViewModel(
    private val email: String,
    private val emailId: String,
    registrationToken: String,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var verificationToken: String = registrationToken

    val state: StateFlow<OtpVerifyState>
        field = MutableStateFlow(OtpVerifyState())

    private val _events = Channel<OtpVerifyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (emailId.isEmpty() && verificationToken.isEmpty()) {
            resendOTP()
        }
    }
    fun onAction(action: OtpVerifyAction) {
        when (action) {
            is OtpVerifyAction.OnCodeChange -> {
                val digits = action.code.filter { it.isDigit() }.take(OTP_LENGTH)
                state.update { it.copy(code = digits, error = null) }
            }

            OtpVerifyAction.OnVerifyClick -> verify()
            OtpVerifyAction.OnResendClick -> resendOTP()
        }
    }

    private fun verify() {
        val current = state.value
        if (current.code.length != OTP_LENGTH) return

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null, info = null) }
            authRepository.verifyOtp(
                VerificationInput(
                    code = current.code,
                    emailId = emailId,
                    registrationToken = verificationToken
                )
            )
                .onSuccess {
                    state.update { it.copy(isLoading = false) }
                    _events.send(OtpVerifyEvent.Verified)
                }
                .onFailure { error, message ->
                    state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }

    private fun resendOTP() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null, info = null) }
            authRepository.resendOtp(email)
                .onSuccess { result ->
                    verificationToken = result.verificationToken
                    state.update {
                        it.copy(
                            isLoading = false,
                            info = UiText.DynamicString("A new code has been sent.")
                        )
                    }
                }
                .onFailure { error, message ->
                    state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }
}
