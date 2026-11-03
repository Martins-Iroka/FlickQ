package com.martdev.flickq.features.payment.domain.service

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.payment.domain.model.PaymentTransitions
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentUpdate
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackSignatureVerifier
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.PaystackWebhookEvent
import com.martdev.flickq.features.payment.observability.PaymentEvents
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.shared.util.returnValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

//Implement payment events and check unit test is fine
@Singleton
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val reservationService: ReservationService,
    private val paystackClient: PaystackClient,
    private val userRepository: UserRepository,
    private val config: PaystackConfig,
    private val paymentEvents: PaymentEvents
) : PaymentService {

    private val log = LoggerFactory.getLogger(PaymentServiceImpl::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun initializePayment(
        reservationId: Long,
        userId: Long
    ): InitializePaymentResult {
        val reservation = reservationService.getMyReservationById(reservationId, userId)

        if (reservation.status != ReservationStatus.PENDING) {
            throw BadRequestException("Reservation is not pending payment")
        }
        if (Clock.System.now() > reservation.expiresAt) {
            throw BadRequestException("Reservation has expired")
        }

        val existing = paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
        if (existing.any { it.status == PaymentStatus.SUCCESS }) {
            throw ConflictException("Reservation already paid")
        }

        val user = userRepository.getUserById(userId).returnValue()
        val email = user.email.ifBlank {
            throw BadRequestException("Cannot initialize payment without a user email")
        }

        val reference = "mrs_${reservationId}_${UUID.randomUUID().toString().take(12)}"
        val paystackAmount = reservation.totalAmount * 100L

        val response = paystackClient.initializeTransaction(
            email = email,
            amount = paystackAmount,
            callbackUrl = config.callbackUrl.ifBlank { null }
        )

        val data = response.data
            ?: throw BadRequestException(response.message.ifBlank { "Paystack rejected the transaction" })

        val payment = Payment(
            reservationId = reservationId,
            userId = userId,
            reference = data.reference.ifBlank { reference },
            amount = paystackAmount,
            currency = config.currency,
            status = PaymentStatus.PENDING,
            authorizationUrl = data.authorizationUrl,
            accessCode = data.accessCode
        )
        paymentRepository.createPayment(payment).returnValue()

        return InitializePaymentResult(
            authorizationUrl = data.authorizationUrl,
            accessCode = data.accessCode,
            reference = data.reference.ifBlank { reference },
            reservationId = reservationId,
        )
    }

    override suspend fun retrieveInitializedPaymentInfo(
        reservationId: Long,
        userId: Long
    ): InitializePaymentResult {
        val reservation = reservationService.getMyReservationById(reservationId, userId)

        if (reservation.status != ReservationStatus.PENDING) {
            throw BadRequestException("Reservation is not pending payment")
        }

        if (Clock.System.now() > reservation.expiresAt) {
            throw BadRequestException("Reservation has expired")
        }

        val user = userRepository.getUserById(userId).returnValue()

        val e = runCatching {
            paymentRepository.getPaymentByReservationId(reservationId).returnValue()
        }.getOrNull()

        return if (e != null) {
            if (e.status != PaymentStatus.PENDING)
                throw ConflictException("Payment status is ${e.status}")

            InitializePaymentResult(
                authorizationUrl = e.authorizationUrl.orEmpty().ifEmpty { "NA" },
                reservationId = reservationId,
                reference = e.reference
            )
        } else {
            val email = user.email.ifBlank {
                throw BadRequestException("Cannot initialize payment without a user email")
            }
            val paystackAmount = reservation.totalAmount * 100L

            val response = paystackClient.initializeTransaction(
                email = email,
                amount = paystackAmount,
                callbackUrl = config.callbackUrl.ifBlank { null }
            )

            val data = response.data
                ?: throw BadRequestException(response.message.ifBlank { "Paystack rejected the transaction" })

            val payment = Payment(
                reservationId = reservationId,
                userId = userId,
                reference = data.reference,
                amount = paystackAmount,
                currency = config.currency,
                status = PaymentStatus.PENDING,
                authorizationUrl = data.authorizationUrl,
                accessCode = data.accessCode
            )
            paymentRepository.createPayment(payment).returnValue()

            InitializePaymentResult(
                authorizationUrl = data.authorizationUrl,
                accessCode = data.accessCode,
                reference = data.reference,
                reservationId = reservationId,
            )
        }
    }

    override suspend fun verifyPayment(reference: String, requestingUserId: Long?): Payment {
        val payment = paymentRepository.getPaymentByReference(reference).returnValue()

        if (requestingUserId != null && payment.userId != requestingUserId) {
            throw ForbiddenException()
        }

        if (payment.status !in PaymentTransitions.CHARGEABLE) {
            return payment
        }

        if (payment.status == PaymentStatus.SUCCESS || payment.status == PaymentStatus.FAILED) {
            return payment
        }

        val verifyResponse = paystackClient.verifyTransaction(reference)
        val data = verifyResponse.data
            ?: throw BadRequestException(verifyResponse.message.ifBlank { "Paystack returned no transaction data" })

        return applyChargeResult(
            reference = reference,
            paystackStatus = data.status,
            paystackAmount = data.amount,
            paystackTransactionId = data.id.takeIf { it > 0 }?.toString(),
            gatewayResponse = data.gatewayResponse,
            paidAtRaw = data.paidAt,
            reservationId = payment.reservationId
        )
    }

    override suspend fun handleWebhook(rawBody: String, signature: String?) {
        if (!PaystackSignatureVerifier.isValid(rawBody, signature, config.secretKey)) {
            log.warn("Paystack webhook rejected: invalid signature")
            throw UnauthorizedException("Invalid webhook signature")
        }

        val event =
            runCatching { json.decodeFromString(PaystackWebhookEvent.serializer(), rawBody) }
                .getOrElse {
                    log.warn("Paystack webhook rejected: malformed body", it)
                    throw BadRequestException("Malformed webhook payload")
                }

        val data = event.data
        log.info("Paystack webhook event: event={}", event.event)
        when (event.event) {
            "charge.success" -> {
                applyChargeResult(
                    reference = data.reference,
                    paystackStatus = data.status,
                    paystackAmount = data.amount,
                    paystackTransactionId = data.id.takeIf { it > 0 }?.toString(),
                    gatewayResponse = data.gatewayResponse,
                    paidAtRaw = data.paidAt,
                    reservationId = 0
                )
            }

            "refund.processed" -> {
                log.info("Paystack webhook refund.processed ref={}", data.reference)
                log.info("Paystack webhook refund.processed ref2={}", data.transactionReference)
                val refundedAt = parseInstantOrNow(data.paidAt)
                val update =
                    paymentRepository.markRefunded(data.reference.ifEmpty { data.transactionReference }, refundedAt).returnValue()
                when (update.result) {
                    PaymentUpdate.Result.APPLIED ->
                        paymentEvents.refundProcessed(update.payment.id, update.payment.amount)
                    PaymentUpdate.Result.REJECTED ->
                        logRejected("refund.processed", data.reference, update.previousStatus, PaymentStatus.REFUNDED)
                    PaymentUpdate.Result.NO_OP -> Unit
                }
            }

            "refund.failed" -> {
                val update = paymentRepository
                    .markRefundFailed(data.reference, data.gatewayResponse)
                    .returnValue()
                if (update.result == PaymentUpdate.Result.REJECTED) {
                    // e.g. a late refund.failed after refund.processed: REFUNDED is final.
                    logRejected(
                        "refund.failed",
                        data.reference,
                        update.previousStatus,
                        PaymentStatus.REFUND_FAILED
                    )
                }
            }

            else -> {
                log.info(
                    "Paystack webhook ignored: event={}, reference={}",
                    event.event,
                    data.reference
                )
            }
        }
    }

    override suspend fun refundPaymentForReservation(reservationId: Long): Payment? {
        val payments = paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
        // Every charge goes back, a not-yet-settled duplicate included; the booking's own payment is returned.
        val refundable = payments.filter { it.status in PaymentTransitions.REFUNDABLE }.sortedBy { it.id }
        if (refundable.isEmpty()) return null
        return refundable.map { refund(it.reference) }.first()
    }

    private suspend fun refund(
        reference: String,
        notifyReason: NotificationEvent.PaymentRefunded.Reason? = null
    ): Payment {
        val claim = paymentRepository.markRefundPending(reference).returnValue()
        if (!claim.transitioned) {
            throw ConflictException("A refund for this reservation is already in progress")
        }
        // Claimed: finish the Paystack call and record its outcome even if the caller is cancelled (shutdown,
        // client gone), or the row sits in REFUND_PENDING with no refund behind it.
        withContext(NonCancellable) {
            val response = try {
                paystackClient.refundTransaction(reference, amount = null)
            } catch (e: Exception) {
                paymentRepository.markRefundFailed(reference, e.message).returnValue()
                throw e
            }

            if (!response.status) {
                paymentRepository.markRefundFailed(reference, response.message).returnValue()
                throw BadRequestException(response.message.ifBlank { "Paystack rejected the refund" })
            }
            // Only once Paystack has taken the refund, so a rejected one never tells the customer otherwise.
            if (notifyReason != null) {
                runCatching { paymentRepository.enqueueRefundNotice(reference, notifyReason).returnValue() }
                    .onFailure { log.warn("Refund push not enqueued (ref={})", reference, it) }
            }
        }
        // Final REFUNDED state is confirmed by webhook (refund.processed), or by the reconciler if that never comes.
        return paymentRepository.getPaymentByReference(reference).returnValue()
    }

    override suspend fun getPaymentsByReservationId(reservationId: Long): List<Payment> {
        return paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
    }

    override suspend fun reconcilePendingPayments() {
        val now = Clock.System.now()
        val verifyAfter = now.minus(15.minutes)
        val giveUpBefore = now.minus(24.hours)

        val pending =
            paymentRepository.findPendingPaymentsOlderThan(verifyAfter, giveUpBefore).returnValue()
        if (pending.isNotEmpty()) log.info("Reconciling {} stuck PENDING payment(s)", pending.size)
        pending.forEach { payment ->
            runCatching { verifyPayment(payment.reference, requestingUserId = null) }
                .onFailure {
                    log.warn(
                        "Reconciliation failed for payment ref={}",
                        payment.reference,
                        it
                    )
                }
        }

        // Paid but never confirmed (a confirm that failed after the charge settled), or paid twice. verifyPayment
        // stops at SUCCESS, so this is the only retry. A row leaves the set once it's CONFIRMED or its refund started.
        val unsettled = paymentRepository.findUnsettledSuccessfulPayments().returnValue()
        if (unsettled.isNotEmpty()) log.info("Settling {} paid but unconfirmed reservation(s)", unsettled.size)
        unsettled.forEach { payment ->
            runCatching { settleSuccessfulCharge(payment) }
                .onFailure {
                    log.warn(
                        "Settlement failed again for payment ref={} (reservation={})",
                        payment.reference, payment.reservationId, it
                    )
                }
        }

        // refund.processed should settle these; if it never arrives, ask Paystack. Never resubmits a refund:
        // we can't tell "Paystack still processing" from "never reached Paystack", and a resubmit could pay out twice.
        // Past two weeks Paystack's answer won't change, so those are only counted, not re-checked.
        val refunding = paymentRepository.findRefundPendingOlderThan(now.minus(1.hours)).returnValue()
        val stuck = refunding.count { payment ->
            val settled = payment.updatedAt > now.minus(14.days) &&
                    runCatching { settlePendingRefund(payment, now) }
                        .onFailure { log.warn("Refund status check failed for payment ref={}", payment.reference, it) }
                        .getOrDefault(false)
            !settled && payment.updatedAt < now.minus(3.days)
        }
        if (stuck > 0) paymentEvents.refundsStuck(stuck)
    }

    /** True once the payment is REFUNDED. A fully refunded Paystack transaction reports status "reversed". */
    private suspend fun settlePendingRefund(payment: Payment, now: Instant): Boolean {
        val data = paystackClient.verifyTransaction(payment.reference).data
        if (data?.status?.lowercase() != "reversed") return false
        val update = paymentRepository.markRefunded(payment.reference, now).returnValue()
        if (update.transitioned) paymentEvents.refundProcessed(update.payment.id, update.payment.amount)
        return true
    }

    /**
     * Single source of truth for applying a Paystack charge outcome to our payment + reservation state.
     * The repository's [PaymentRepository.applyChargeResult] is itself idempotent and amount-validated;
     * here we additionally confirm the reservation when the payment succeeds.
     */
    private suspend fun applyChargeResult(
        reference: String,
        paystackStatus: String,
        paystackAmount: Long,
        paystackTransactionId: String?,
        gatewayResponse: String?,
        paidAtRaw: String?,
        reservationId: Long,
    ): Payment {
        val mappedStatus = when (paystackStatus.lowercase()) {
            "success" -> PaymentStatus.SUCCESS
            "failed" -> PaymentStatus.FAILED
            "reversed" -> {
                // A reversal is a refund; refunds settle only via refund.processed so their lifecycle lives in one place.
                log.warn("event=payment.charge.reversed_ignored ref={}", reference)
                return paymentRepository.getPaymentByReference(reference).returnValue()
            }
            "abandoned" -> PaymentStatus.ABANDONED
            else -> PaymentStatus.PENDING
        }

        val update = paymentRepository.applyChargeResult(
            reference = reference,
            status = mappedStatus,
            gatewayResponse = gatewayResponse,
            paystackTransactionId = paystackTransactionId,
            paidAt = paidAtRaw?.let { parseInstantOrNull(it) },
            amountFromGateway = paystackAmount,
        ).returnValue()
        val updated = update.payment

        when (update.result) {
            PaymentUpdate.Result.APPLIED -> publishTransition(updated, mappedStatus)
            PaymentUpdate.Result.REJECTED -> logRejected("charge", reference, update.previousStatus, mappedStatus)
            PaymentUpdate.Result.NO_OP -> Unit
        }

        if (updated.status == PaymentStatus.SUCCESS) {
            runCatching { settleSuccessfulCharge(updated) }
                .onFailure {
                    // Payment SUCCESS is durable and the reservation is still PENDING (or its refund failed to
                    // start): reconcilePendingPayments picks up SUCCESS payments with an unconfirmed reservation.
                    log.warn(
                        "Payment SUCCESS but reservation confirm failed (reservation={}, ref={})",
                        updated.reservationId, reference, it
                    )
                }
        }

        return updated
    }

    /**
     * Gives a SUCCESS payment what it paid for. If the reservation was cancelled first (expiry sweep, or a
     * cancel while the charge was in flight) its seats are already released, so the money goes back instead.
     * Any other failure is rethrown with the reservation still PENDING, for the reconciler to retry.
     */
    private suspend fun settleSuccessfulCharge(payment: Payment) {
        val confirmFailure = try {
            reservationService.confirmReservationFromPayment(payment.reservationId)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        if (confirmFailure == null) {
            refundDuplicateCharges(payment.reservationId)
            return
        }
        // Both "cancelled" signals (service BadRequest, repository Conflict) are re-checked against the row.
        if (reservationService.getReservationById(payment.reservationId).status != ReservationStatus.CANCELLED) {
            throw confirmFailure
        }
        refundAutomatically(payment, NotificationEvent.PaymentRefunded.Reason.RESERVATION_CANCELLED)
    }

    /**
     * Two checkouts for one reservation can both be paid. The lowest-id SUCCESS payment keeps the booking and the
     * rest go back; settling either charge applies the same rule, so the order they land in doesn't matter.
     */
    private suspend fun refundDuplicateCharges(reservationId: Long) {
        paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
            .filter { it.status == PaymentStatus.SUCCESS }
            .sortedBy { it.id }
            .drop(1)
            .forEach { refundAutomatically(it, NotificationEvent.PaymentRefunded.Reason.DUPLICATE_CHARGE) }
    }

    private suspend fun refundAutomatically(payment: Payment, reason: NotificationEvent.PaymentRefunded.Reason) {
        val reasonTag = reason.name.lowercase()
        try {
            refund(payment.reference, notifyReason = reason)
            paymentEvents.automaticRefund(payment.reservationId, payment.amount, reasonTag, "refund_pending")
        } catch (e: ConflictException) {
            // A concurrent webhook/verify/reconciler run already claimed this refund.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = runCatching { paymentRepository.getPaymentByReference(payment.reference).returnValue().status }
                .getOrNull()
            if (status == PaymentStatus.REFUND_FAILED) {
                // Paystack refused it: out of the reconciler's set, into the admin refund retry.
                paymentEvents.automaticRefund(payment.reservationId, payment.amount, reasonTag, "refund_failed")
                log.error(
                    "Automatic refund failed (reservation={}, ref={}, reason={})",
                    payment.reservationId, payment.reference, reasonTag, e
                )
            } else {
                // The claim itself failed (still SUCCESS): the reconciler tries again next run.
                log.warn("Automatic refund not started (ref={}, reason={})", payment.reference, reasonTag, e)
            }
        }
    }

    private fun publishTransition(payment: Payment, requested: PaymentStatus) {
        when (payment.status) {
            PaymentStatus.SUCCESS -> paymentEvents.paymentSucceeded(
                payment.reservationId,
                payment.amount
            )

            PaymentStatus.FAILED -> paymentEvents.paymentFailed(
                reservationId = payment.reservationId,
                reason = if (requested == PaymentStatus.SUCCESS) "amount_mismatch" else "failed",
                gatewayErrorCode = payment.gatewayResponse
            )

            PaymentStatus.REFUNDED -> paymentEvents.refundProcessed(payment.id, payment.amount)
            else -> Unit
        }
    }

    private fun logRejected(source: String, reference: String, from: PaymentStatus, to: PaymentStatus) {
        log.warn("event=payment.transition.rejected source={} ref={} from={} to={}", source, reference, from, to)
    }

    private fun parseInstantOrNull(s: String): Instant? =
        runCatching { Instant.parse(s) }.getOrNull()

    private fun parseInstantOrNow(s: String?): Instant =
        s?.let { parseInstantOrNull(it) } ?: Clock.System.now()
}
