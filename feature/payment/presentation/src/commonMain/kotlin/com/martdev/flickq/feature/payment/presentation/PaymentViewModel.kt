package com.martdev.flickq.feature.payment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
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
import kotlin.time.Duration.Companion.seconds

enum class PaymentPhase { INITIALIZING, READY_TO_PAY, AWAITING_PAYMENT, CONFIRMED }

data class PaymentState(
    val phase: PaymentPhase = PaymentPhase.INITIALIZING,
    val reference: String = "",
    val amountLabel: String = "",
    val authorizationUrl: String? = null,
    val error: UiText? = null
) {
    val isWorking: Boolean get() = error == null && phase != PaymentPhase.CONFIRMED
}

sealed interface PaymentAction {
    data object OnProceedToPayment : PaymentAction
    data object OnDoneClick : PaymentAction
    data object OnRetry : PaymentAction
    data object OnBackClick : PaymentAction
}

sealed interface PaymentEvent {
    data object Done : PaymentEvent
    data object NavigateBack : PaymentEvent

    /** The reservation can no longer be paid (expired/cancelled) — caller pops to seat selection. */
    data object ReservationExpired : PaymentEvent
}

/**
 * Drives the real Paystack hand-off: initialize a payment, then — once the user taps
 * "Proceed to payment" — open the returned `authorization_url` in an in-app browser
 * ([UrlOpener]) and poll `verify/{reference}` with bounded backoff until the gateway
 * reports a terminal state.
 *
 * Opening is gated behind an explicit user gesture (the proceed action) rather than fired
 * automatically: web browsers block `window.open` unless it happens inside a click handler.
 * On fakes the authorization url is absent, so verify confirms immediately and the screen
 * never stops at [PaymentPhase.READY_TO_PAY].
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
            // Open synchronously in the gesture's call stack so the browser honours it,
            // then poll on a coroutine.
            PaymentAction.OnProceedToPayment -> openCheckoutAndPoll()
            PaymentAction.OnDoneClick -> viewModelScope.launch {
                _events.send(PaymentEvent.Done)
            }
            PaymentAction.OnRetry -> retry()
            PaymentAction.OnBackClick -> viewModelScope.launch {
                _events.send(PaymentEvent.NavigateBack)
            }
        }
    }

    private fun openCheckoutAndPoll() {
        val url = _state.value.authorizationUrl ?: return
        val reference = _state.value.reference
        _state.update { it.copy(phase = PaymentPhase.AWAITING_PAYMENT, error = null) }
        urlOpener.launchCheckout(url,
            "flickq",
            onCancel = {
                _state.update {
                    it.copy(error = UiText.DynamicString("Payment process was cancelled."))
                }
            },
            onResult = {
                viewModelScope.launch { pollUntilResolved(reference) }
                /*status?.let { s ->
                    if (s == "success") {
                        _state.update {
                            it.copy(
                                phase = PaymentPhase.CONFIRMED,
                                reference = reference ?: "N/A",
                                amountLabel = formatNaira(amount?.toLongOrNull() ?: 0),
                                error = null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(error = UiText.DynamicString("Payment was not completed. Please try again."))
                        }
                    }
                }*/
            })
//        viewModelScope.launch { pollUntilResolved(_state.value.reference) }
    }

    /**
     * Retry after an error. With no reference the transaction was never created, so restart.
     * Otherwise re-open the checkout (the "Try again" tap is itself a gesture) and re-poll —
     * re-initializing would create a duplicate transaction.
     */
    private fun retry() {
        val reference = _state.value.reference
        if (reference.isBlank()) {
            pay()
        } else {
            openCheckoutAndPoll()
        }
    }

    private fun pay() {
        viewModelScope.launch {
            _state.update {
                it.copy(phase = PaymentPhase.INITIALIZING, error = null, reference = "", authorizationUrl = null)
            }
            paymentRepository.initializePayment(reservationId)
                .onSuccess { initiated ->
                    val url = initiated.authorizationUrl?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        // Real gateway: wait for the user to tap "Proceed to payment".
                        _state.update {
                            it.copy(
                                phase = PaymentPhase.READY_TO_PAY,
                                reference = initiated.reference,
                                authorizationUrl = url
                            )
                        }
                    } else {
                        // No hand-off (fakes): go straight to verifying.
                        _state.update {
                            it.copy(phase = PaymentPhase.AWAITING_PAYMENT, reference = initiated.reference)
                        }
                        pollUntilResolved(initiated.reference)
                    }
                }
                .onFailure { error, message ->
                    // A reservation that expired or was cancelled can't be paid — the server
                    // rejects the hand-off with 409/400. Don't show "payment failed" + retry
                    // (retry would just fail again); send the user back to re-pick seats.
                    if (error.isReservationNoLongerPayable()) {
                        _events.send(PaymentEvent.ReservationExpired)
                    } else {
                        _state.update { it.copy(error = resolveErrorText(message, error.toUiText())) }
                    }
                }
        }
    }

    // The reservation hold lapsed (expiresAt passed → seats released) or it was cancelled.
    private fun DataError.isReservationNoLongerPayable(): Boolean =
        this == DataError.Network.CONFLICT || this == DataError.Network.BAD_REQUEST

    private suspend fun pollUntilResolved(reference: String) {
        var lastError: DataError? = null
        var lastMessage: String? = null
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) delay(pollDelayMillis.seconds)
            when (val result = paymentRepository.verifyPayment(reference)) {
                is Result.Success -> {
                    lastError = null
                    lastMessage = null
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
                is Result.Error -> {
                    lastError = result.error
                    lastMessage = result.message
                }
            }
        }
        _state.update {
            it.copy(
                error = lastError?.let { error -> resolveErrorText(lastMessage, error.toUiText()) }
                    ?: UiText.DynamicString("We couldn't confirm your payment yet. If you've completed it, tap retry.")
            )
        }
    }

    private companion object {
        const val DEFAULT_POLL_DELAY_MILLIS = 2_500L
        const val DEFAULT_MAX_POLL_ATTEMPTS = 20
    }
}
