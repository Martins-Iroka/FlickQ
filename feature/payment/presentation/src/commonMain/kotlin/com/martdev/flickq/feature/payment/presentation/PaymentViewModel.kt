package com.martdev.flickq.feature.payment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PaymentPhase { INITIALIZING, PROCESSING, CONFIRMED }

data class PaymentState(
    val phase: PaymentPhase = PaymentPhase.INITIALIZING,
    val reference: String = "",
    val amountLabel: String = "",
    val error: UiText? = null
) {
    val isWorking: Boolean get() = error == null && phase != PaymentPhase.CONFIRMED
}

sealed interface PaymentAction {
    data object OnDoneClick : PaymentAction
    data object OnRetry : PaymentAction
    data object OnBackClick : PaymentAction
}

sealed interface PaymentEvent {
    data object Done : PaymentEvent
    data object NavigateBack : PaymentEvent
}

class PaymentViewModel(
    private val reservationId: Long,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()

    private val _events = Channel<PaymentEvent>()
    val events = _events.receiveAsFlow()

    init {
        pay()
    }

    fun onAction(action: PaymentAction) {
        when (action) {
            PaymentAction.OnDoneClick -> viewModelScope.launch {
                _events.send(PaymentEvent.Done)
            }
            PaymentAction.OnRetry -> pay()
            PaymentAction.OnBackClick -> viewModelScope.launch {
                _events.send(PaymentEvent.NavigateBack)
            }
        }
    }

    private fun pay() {
        viewModelScope.launch {
            _state.update { it.copy(phase = PaymentPhase.INITIALIZING, error = null) }
            paymentRepository.initializePayment(reservationId)
                .onSuccess { initiated ->
                    // Real Paystack opens initiated.authorizationUrl here (platform UrlOpener);
                    // on fakes we go straight to verifying the reference.
                    _state.update { it.copy(phase = PaymentPhase.PROCESSING, reference = initiated.reference) }
                    paymentRepository.verifyPayment(initiated.reference)
                        .onSuccess { paid ->
                            _state.update {
                                it.copy(
                                    phase = PaymentPhase.CONFIRMED,
                                    reference = paid.reference,
                                    amountLabel = formatNaira(paid.amount)
                                )
                            }
                        }
                        .onFailure { error ->
                            _state.update { it.copy(error = error.toUiText()) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.toUiText()) }
                }
        }
    }
}
