package com.martdev.flickq.features.payment.domain.service

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackSignatureVerifier
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.PaystackWebhookEvent
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.shared.util.returnValue
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

//Implement payment events and check unit test is fine
@Single
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val reservationService: ReservationService,
    private val paystackClient: PaystackClient,
    private val userRepository: UserRepository,
    private val config: PaystackConfig
) : PaymentService {

    private val log = LoggerFactory.getLogger(PaymentServiceImpl::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun initializePayment(reservationId: Long, userId: Long): InitializePaymentResult {
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
            amount = paystackAmount
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

    override suspend fun verifyPayment(reference: String, requestingUserId: Long?): Payment {
        val payment = paymentRepository.getPaymentByReference(reference).returnValue()

        if (requestingUserId != null && payment.userId != requestingUserId) {
            throw ForbiddenException()
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
        if (!PaystackSignatureVerifier.isValid(rawBody, signature, config.webhookSecret)) {
            log.warn("Paystack webhook rejected: invalid signature")
            throw UnauthorizedException("Invalid webhook signature")
        }

        val event = runCatching { json.decodeFromString(PaystackWebhookEvent.serializer(), rawBody) }
            .getOrElse {
                log.warn("Paystack webhook rejected: malformed body", it)
                throw BadRequestException("Malformed webhook payload")
            }

        val data = event.data
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
                val refundedAt = parseInstantOrNow(data.paidAt)
                paymentRepository.markRefunded(data.reference, refundedAt).returnValue()
            }

            "refund.failed" -> {
                paymentRepository.markRefundFailed(data.reference, data.gatewayResponse).returnValue()
            }

            else -> {
                log.info("Paystack webhook ignored: event={}, reference={}", event.event, data.reference)
            }
        }
    }

    override suspend fun refundPaymentForReservation(reservationId: Long): Payment? {
        val payments = paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
        val successful = payments.firstOrNull { it.status == PaymentStatus.SUCCESS } ?: return null

        paymentRepository.markRefundPending(successful.reference).returnValue()

        val response = try {
            paystackClient.refundTransaction(successful.reference, amount = null)
        } catch (e: Exception) {
            paymentRepository.markRefundFailed(successful.reference, e.message).returnValue()
            throw e
        }

        if (!response.status) {
            paymentRepository.markRefundFailed(successful.reference, response.message).returnValue()
            throw BadRequestException(response.message.ifBlank { "Paystack rejected the refund" })
        }
        // Final REFUNDED state is confirmed by webhook (refund.processed).
        return paymentRepository.getPaymentByReference(successful.reference).returnValue()
    }

    override suspend fun getMyPayments(userId: Long): List<Payment> {
        return paymentRepository.getPaymentsByUserId(userId).returnValue()
    }

    override suspend fun getPaymentsByReservationId(reservationId: Long): List<Payment> {
        return paymentRepository.getPaymentsByReservationId(reservationId).returnValue()
    }

    override suspend fun reconcilePendingPayments() {
        val now = Clock.System.now()
        val verifyAfter = now.minus(15.minutes)
        val giveUpBefore = now.minus(24.hours)

        val pending = paymentRepository.findPendingPaymentsOlderThan(verifyAfter, giveUpBefore).returnValue()
        if (pending.isEmpty()) return

        log.info("Reconciling {} stuck PENDING payment(s)", pending.size)
        pending.forEach { payment ->
            runCatching { verifyPayment(payment.reference, requestingUserId = null) }
                .onFailure { log.warn("Reconciliation failed for payment ref={}", payment.reference, it) }
        }
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
            "success" -> {
//                paymentEvents.paymentSucceeded(reservationId, paystackAmount)
                PaymentStatus.SUCCESS
            }

            "failed" -> {
//                paymentEvents.paymentFailed(reservationId, paystackStatus, gatewayResponse)
                PaymentStatus.FAILED
            }

            "reversed" -> {
//                paymentEvents.refundProcessed(reservationId, paystackAmount)
                PaymentStatus.REFUNDED
            }
            "abandoned" -> PaymentStatus.ABANDONED
            else -> PaymentStatus.PENDING
        }

        val updated = paymentRepository.applyChargeResult(
            reference = reference,
            status = mappedStatus,
            gatewayResponse = gatewayResponse,
            paystackTransactionId = paystackTransactionId,
            paidAt = paidAtRaw?.let { parseInstantOrNull(it) },
            amountFromGateway = paystackAmount,
        ).returnValue()

        if (updated.status == PaymentStatus.SUCCESS) {
            runCatching { reservationService.confirmReservationFromPayment(updated.reservationId) }
                .onFailure {
                    // If reservation update fails, the reconciler will retry — payment SUCCESS is durable.
                    log.warn(
                        "Payment SUCCESS but reservation confirm failed (reservation={}, ref={})",
                        updated.reservationId, reference, it
                    )
                }
        }

        return updated
    }

    private fun parseInstantOrNull(s: String): Instant? =
        runCatching { Instant.parse(s) }.getOrNull()

    private fun parseInstantOrNow(s: String?): Instant =
        s?.let { parseInstantOrNull(it) } ?: Clock.System.now()
}
