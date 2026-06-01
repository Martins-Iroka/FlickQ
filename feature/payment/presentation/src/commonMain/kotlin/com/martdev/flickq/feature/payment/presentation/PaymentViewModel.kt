package com.martdev.flickq.feature.payment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import com.martdev.flickq.payment.model.PaymentStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PaymentPhase { INITIALIZING, AWAITING_PAYMENT, CONFIRMED }

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

/**
 * Drives the real Paystack hand-off: initialize a payment, open the returned
 * `authorization_url` in an in-app browser ([UrlOpener]), then poll `verify/{reference}`
 * with bounded backoff until the gateway reports a terminal state. On fakes the
 * authorization url is absent and verify confirms immediately, so the flow still completes.
 *
 * [pollDelayMillis]/[maxPollAttempts] are injectable so tests can run without real delays.
 */
class PaymentViewModel(
    private val reservationId: Long,
    private val paymentRepository: PaymentRepository,
    private val urlOpener: UrlOpener,
    private val pollDelayMillis: Long = DEFAULT_POLL_DELAY_MILLIS,
    private val maxPollAttempts: Int = DEFAULT_MAX_POLL_ATTEMPTS,
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
            PaymentAction.OnRetry -> retry()
            PaymentAction.OnBackClick -> viewModelScope.launch {
                _events.send(PaymentEvent.NavigateBack)
            }
        }
    }

    /**
     * Retry after an error: if a reference already exists the transaction was created, so we
     * only re-poll (re-initializing would create a duplicate transaction). Otherwise restart.
     */
    private fun retry() {
        val reference = _state.value.reference
        if (reference.isBlank()) {
            pay()
        } else {
            _state.update { it.copy(phase = PaymentPhase.AWAITING_PAYMENT, error = null) }
            viewModelScope.launch { pollUntilResolved(reference) }
        }
    }

    private fun pay() {
        viewModelScope.launch {
            _state.update { it.copy(phase = PaymentPhase.INITIALIZING, error = null, reference = "") }
            paymentRepository.initializePayment(reservationId)
                .onSuccess { initiated ->
                    _state.update { it.copy(reference = initiated.reference) }
                    initiated.authorizationUrl
                        ?.takeIf { it.isNotBlank() }
                        ?.let { urlOpener.open(it) }
                    _state.update { it.copy(phase = PaymentPhase.AWAITING_PAYMENT) }
                    pollUntilResolved(initiated.reference)
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.toUiText()) }
                }
        }
    }

    private suspend fun pollUntilResolved(reference: String) {
        var lastError: DataError? = null
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) delay(pollDelayMillis)
            when (val result = paymentRepository.verifyPayment(reference)) {
                is Result.Success -> {
                    lastError = null
                    val payment = result.data
                    when (payment.status) {
                        PaymentStatus.SUCCESS -> {
                            _state.update {
                                it.copy(
                                    phase = PaymentPhase.CONFIRMED,
                                    reference = payment.reference,
                                    amountLabel = formatNaira(payment.amount),
                                    error = null
                                )
                            }
                            return
                        }
                        PaymentStatus.FAILED, PaymentStatus.ABANDONED -> {
                            _state.update {
                                it.copy(error = UiText.DynamicString("Payment was not completed. Please try again."))
                            }
                            return
                        }
                        // INITIATED / PENDING / refund states — not yet resolved; keep polling.
                        else -> Unit
                    }
                }
                // Verify can fail transiently while the gateway settles; keep polling and only
                // surface the error if every attempt fails.
                is Result.Error -> lastError = result.error
            }
        }
        _state.update {
            it.copy(
                error = lastError?.toUiText()
                    ?: UiText.DynamicString("We couldn't confirm your payment yet. If you've completed it, tap retry.")
            )
        }
    }

    private companion object {
        const val DEFAULT_POLL_DELAY_MILLIS = 2_500L
        const val DEFAULT_MAX_POLL_ATTEMPTS = 20
    }
}
