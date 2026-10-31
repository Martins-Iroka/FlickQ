package com.martdev.flickq.features.payment.domain.service

import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentUpdate
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.InitializeResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse
import com.martdev.flickq.features.payment.observability.PaymentEvents
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@ExtendWith(MockKExtension::class)
class PaymentServiceImplTest {

    @MockK
    private lateinit var paymentRepository: PaymentRepository

    @MockK
    private lateinit var reservationService: ReservationService

    @MockK
    private lateinit var paystackClient: PaystackClient

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK(relaxUnitFun = true)
    private lateinit var paymentEvents: PaymentEvents

    private val webhookSecret = "sk_test_secret"
    private val config = PaystackConfig(
        secretKey = webhookSecret,
        publicKey = "pk_test",
        baseUrl = "https://api.paystack.co",
        callbackUrl = "https://example.test/callback",
        webhookSecret = webhookSecret,
        currency = "NGN",
    )

    private lateinit var service: PaymentServiceImpl

    private val userId = 42L
    private val reservationId = 1000L
    private val pendingReservation = Reservation(
        id = reservationId,
        userId = userId,
        showtimeId = 7L,
        status = ReservationStatus.PENDING,
        totalAmount = 5000, // ₦5,000 — major unit
        expiresAt = Clock.System.now().plus(10.minutes),
    )
    private val storedReference = "mrs_${reservationId}_abc123"
    private val pendingPayment = Payment(
        id = 1L,
        reservationId = reservationId,
        userId = userId,
        reference = storedReference,
        amount = 500000L, // 5000 * 100
        currency = "NGN",
        status = PaymentStatus.PENDING,
    )

    @BeforeEach
    fun setup() {
        service = PaymentServiceImpl(
            paymentRepository,
            reservationService,
            paystackClient,
            userRepository,
            config, paymentEvents
        )
    }

    @Test
    fun `initializePayment converts amount to kobo and persists a PENDING payment`() = runTest {
        coEvery {
            reservationService.getMyReservationById(
                reservationId,
                userId
            )
        } returns pendingReservation
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
            emptyList()
        )
        coEvery { userRepository.getUserById(userId) } returns DataResult.Success(
            UserData(
                id = userId,
                email = "u@x.io"
            )
        )
        coEvery {
            paystackClient.initializeTransaction(
                email = any(),
                amount = any(),
                any()
            )
        } returns InitializeResponse(
            status = true,
            message = "Authorization URL created",
            data = InitializeResponse.InitializeData(
                authorizationUrl = "https://paystack.co/pay/abc",
                accessCode = "code",
                reference = "ref_returned",
            ),
        )
        val savedPayment = slot<Payment>()
        coEvery { paymentRepository.createPayment(capture(savedPayment)) } answers {
            DataResult.Success(savedPayment.captured.copy(id = 99L))
        }

        val result = service.initializePayment(reservationId, userId)

//        assertEquals(500000L, amountCaptured.captured)
        assertEquals(500000L, savedPayment.captured.amount)
        assertEquals(PaymentStatus.PENDING, savedPayment.captured.status)
        assertEquals("ref_returned", result.reference)
        assertEquals("https://paystack.co/pay/abc", result.authorizationUrl)
    }

    @Test
    fun `initializePayment rejects already-paid reservation with Conflict`() = runTest {
        coEvery {
            reservationService.getMyReservationById(
                reservationId,
                userId
            )
        } returns pendingReservation
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
            listOf(pendingPayment.copy(status = PaymentStatus.SUCCESS))
        )

        assertThrows<ConflictException> {
            service.initializePayment(reservationId, userId)
        }
    }

    @Test
    fun `initializePayment rejects expired reservation with BadRequest`() = runTest {
        val expired = pendingReservation.copy(expiresAt = Clock.System.now().minus(1.minutes))
        coEvery { reservationService.getMyReservationById(reservationId, userId) } returns expired

        assertThrows<BadRequestException> {
            service.initializePayment(reservationId, userId)
        }
    }

    @Test
    fun `retrieve initialized payment returns result`() {
        val authorizationUrl = "authorization_url"
        runTest {
            coEvery {
                reservationService.getMyReservationById(
                    any(),
                    any()
                )
            } returns pendingReservation
            coEvery { userRepository.getUserById(any()) } returns DataResult.Success(UserData())
            coEvery { paymentRepository.getPaymentByReservationId(any()) } returns DataResult.Success(
                Payment(
                    status = PaymentStatus.PENDING,
                    authorizationUrl = authorizationUrl
                )
            )

            val result = service.retrieveInitializedPaymentInfo(reservationId, userId)

            assertEquals(authorizationUrl, result.authorizationUrl)
        }
    }

    @Test
    fun `verifyPayment is a no-op when payment is already SUCCESS`() = runTest {
        val confirmed = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            confirmed
        )

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.SUCCESS, result.status)
        coVerify(exactly = 0) { paystackClient.verifyTransaction(any()) }
    }

    @Test
    fun `verifyPayment throws Forbidden when requesting user does not own payment`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            pendingPayment
        )

        assertThrows<ForbiddenException> {
            service.verifyPayment(storedReference, requestingUserId = 999L)
        }
    }

    @Test
    fun `verifyPayment promotes payment to SUCCESS and confirms reservation`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            pendingPayment
        )
        coEvery { paystackClient.verifyTransaction(storedReference) } returns VerifyResponse(
            status = true,
            message = "Verification successful",
            data = VerifyResponse.VerifyData(
                id = 7777L,
                status = "success",
                reference = storedReference,
                amount = pendingPayment.amount, // matches → no fraud guard trigger
                currency = "NGN",
                paidAt = "2026-05-19T12:00:00Z",
                gatewayResponse = "Approved",
            ),
        )
        coEvery {
            paymentRepository.applyChargeResult(
                reference = storedReference,
                status = PaymentStatus.SUCCESS,
                gatewayResponse = "Approved",
                paystackTransactionId = "7777",
                paidAt = any(),
                amountFromGateway = pendingPayment.amount,
            )
        } returns applied(pendingPayment.copy(status = PaymentStatus.SUCCESS))

        coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CONFIRMED)

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.SUCCESS, result.status)
        coVerify { reservationService.confirmReservationFromPayment(reservationId) }
    }

    @Test
    fun `verifyPayment maps non-success Paystack status to FAILED`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            pendingPayment
        )
        coEvery { paystackClient.verifyTransaction(storedReference) } returns VerifyResponse(
            status = true,
            message = "Verification successful",
            data = VerifyResponse.VerifyData(
                status = "failed",
                reference = storedReference,
                amount = pendingPayment.amount,
                gatewayResponse = "Declined",
            ),
        )
        coEvery {
            paymentRepository.applyChargeResult(
                reference = storedReference,
                status = PaymentStatus.FAILED,
                gatewayResponse = "Declined",
                paystackTransactionId = null,
                paidAt = null,
                amountFromGateway = pendingPayment.amount,
            )
        } returns rejected(
            pendingPayment.copy(status = PaymentStatus.FAILED)
        )

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.FAILED, result.status)
        coVerify(exactly = 0) { reservationService.confirmReservationFromPayment(any()) }
    }

    @Test
    fun `handleWebhook rejects invalid signature with Unauthorized`() = runTest {
        val body = """{"event":"charge.success","data":{"reference":"x","status":"success"}}"""

        assertThrows<UnauthorizedException> {
            service.handleWebhook(body, signature = "deadbeef")
        }
    }

    @Test
    fun `handleWebhook with valid signature applies charge result`() = runTest {
        val body =
            """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount},"gateway_response":"OK","paid_at":"2026-05-19T12:00:00Z"}}"""
        val signature = hmacSha512(body, webhookSecret)

        coEvery {
            paymentRepository.applyChargeResult(
                reference = storedReference,
                status = PaymentStatus.SUCCESS,
                gatewayResponse = "OK",
                paystackTransactionId = "99",
                paidAt = any(),
                amountFromGateway = pendingPayment.amount,
            )
        } returns applied(pendingPayment.copy(status = PaymentStatus.SUCCESS))

        coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CONFIRMED)

        service.handleWebhook(body, signature)

        coVerify {
            paymentRepository.applyChargeResult(
                reference = storedReference,
                status = PaymentStatus.SUCCESS,
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `refundPaymentForReservation returns null when no SUCCESS payment exists`() = runTest {
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
            listOf(pendingPayment) // PENDING, not SUCCESS
        )

        val result = service.refundPaymentForReservation(reservationId)

        assertNull(result)
        coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
    }

    @Test
    fun `refundPaymentForReservation marks REFUND_PENDING and calls Paystack`() = runTest {
        val succeeded = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
            listOf(
                succeeded
            )
        )
        coEvery { paymentRepository.markRefundPending(storedReference) } returns
                applied(
                    succeeded.copy(status = PaymentStatus.REFUND_PENDING),
                    from = PaymentStatus.SUCCESS
                )
        coEvery { paystackClient.refundTransaction(storedReference, amount = null) } returns
                RefundResponse(status = true, message = "Refund queued")
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns
                DataResult.Success(succeeded.copy(status = PaymentStatus.REFUND_PENDING))

        val result = service.refundPaymentForReservation(reservationId)

        assertEquals(result?.status, PaymentStatus.REFUND_PENDING)
        coVerify { paymentRepository.markRefundPending(storedReference) }
        coVerify { paystackClient.refundTransaction(storedReference, null) }
    }

    @Test
    fun `a real SUCCESS transition emits paymentSucceeded with the payment's reservation, not the webhook's 0`() =
        runTest {
//            val rid = slot<Long>()
            val body =
                """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount}}}"""
            coEvery {
                paymentRepository.applyChargeResult(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns applied(pendingPayment.copy(status = PaymentStatus.SUCCESS))

            coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns pendingReservation

            service.handleWebhook(body, hmacSha512(body, webhookSecret))

            verify(exactly = 1) {
                paymentEvents.paymentSucceeded(
                    reservationId,
                    pendingPayment.amount
                )
            }
        }

    @Test
    fun `a replayed SUCCESS emits nothing but still re-attempts confirmation`() = runTest {
        val body =
            """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount}}}"""
        coEvery {
            paymentRepository.applyChargeResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns noOp(pendingPayment.copy(status = PaymentStatus.SUCCESS))

        coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns pendingReservation

        service.handleWebhook(body, hmacSha512(body, webhookSecret))

        verify(exactly = 0) { paymentEvents.paymentSucceeded(any(), any()) }
        coVerify(exactly = 1) { reservationService.confirmReservationFromPayment(reservationId) }
    }

    @Test
    fun `an amount mismatch emits paymentFailed(amount_mismatch) and never confirms`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            pendingPayment
        )
        coEvery { paystackClient.verifyTransaction(storedReference) } returns VerifyResponse(
            status = true, message = "ok",
            data = VerifyResponse.VerifyData(
                status = "success",
                reference = storedReference,
                amount = 1L
            ),
        )
        val failed = pendingPayment.copy(
            status = PaymentStatus.FAILED,
            gatewayResponse = "Amount mismatch: gateway=1, expected=500000"
        )
        coEvery {
            paymentRepository.applyChargeResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns applied(failed)

        service.verifyPayment(storedReference, requestingUserId = userId)

        verify {
            paymentEvents.paymentFailed(
                reservationId,
                "amount_mismatch",
                failed.gatewayResponse
            )
        }
        verify(exactly = 0) { paymentEvents.paymentSucceeded(any(), any()) }
        coVerify(exactly = 0) { reservationService.confirmReservationFromPayment(any()) }
    }

    @Test
    fun `verifyPayment on a payment in the refund lifecycle never calls Paystack`() = runTest {
        listOf(
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUND_FAILED,
            PaymentStatus.REFUNDED
        ).forEach { status ->
            val inRefund = pendingPayment.copy(status = status)
            coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
                inRefund
            )

            assertEquals(
                inRefund,
                service.verifyPayment(storedReference, requestingUserId = userId)
            )
        }
        coVerify(exactly = 0) { paystackClient.verifyTransaction(any()) }
        coVerify(exactly = 0) {
            paymentRepository.applyChargeResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `a reversed charge status is ignored, not written as REFUNDED`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(
            pendingPayment
        )
        coEvery { paystackClient.verifyTransaction(storedReference) } returns VerifyResponse(
            status = true, message = "ok",
            data = VerifyResponse.VerifyData(
                status = "reversed",
                reference = storedReference,
                amount = pendingPayment.amount
            ),
        )

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.PENDING, result.status)
        coVerify(exactly = 0) {
            paymentRepository.applyChargeResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        verify(exactly = 0) { paymentEvents.refundProcessed(any(), any()) }
    }

    @Test
    fun `a replayed charge success on a refunding payment neither confirms nor emits`() = runTest {
        val body =
            """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount}}}"""
        coEvery {
            paymentRepository.applyChargeResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns
                rejected(pendingPayment.copy(status = PaymentStatus.REFUND_PENDING))

        service.handleWebhook(
            body,
            hmacSha512(body, webhookSecret)
        )          // must not throw → 200

        coVerify(exactly = 0) { reservationService.confirmReservationFromPayment(any()) }
        verify(exactly = 0) { paymentEvents.paymentSucceeded(any(), any()) }
    }

    @Test
    fun `refund processed emits refundProcessed only when it applied`() = runTest {
        val body =
            """{"event":"refund.processed","data":{"reference":"$storedReference","status":"processed"}}"""
        val refunded = pendingPayment.copy(status = PaymentStatus.REFUNDED)
        coEvery { paymentRepository.markRefunded(storedReference, any()) } returnsMany listOf(
            applied(refunded, from = PaymentStatus.REFUND_PENDING),
            noOp(refunded),
        )

        service.handleWebhook(body, hmacSha512(body, webhookSecret))
        service.handleWebhook(body, hmacSha512(body, webhookSecret))          // replay

        verify(exactly = 1) { paymentEvents.refundProcessed(refunded.id, refunded.amount) }
    }

    @Test
    fun `rejected refund webhooks are acknowledged without throwing or emitting`() = runTest {
        val processed =
            """{"event":"refund.processed","data":{"reference":"$storedReference","status":"processed"}}"""
        val failed =
            """{"event":"refund.failed","data":{"reference":"$storedReference","status":"failed","gateway_response":"x"}}"""
        coEvery { paymentRepository.markRefunded(storedReference, any()) } returns rejected(
            pendingPayment
        )
        coEvery { paymentRepository.markRefundFailed(storedReference, any()) } returns
                rejected(pendingPayment.copy(status = PaymentStatus.REFUNDED))

        service.handleWebhook(processed, hmacSha512(processed, webhookSecret))
        service.handleWebhook(failed, hmacSha512(failed, webhookSecret))

        verify(exactly = 0) { paymentEvents.refundProcessed(any(), any()) }
    }

    @Test
    fun `a refund someone else already claimed is a 409 and Paystack is not called again`() =
        runTest {
            val succeeded = pendingPayment.copy(status = PaymentStatus.SUCCESS)
            coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
                listOf(succeeded)
            )
            coEvery { paymentRepository.markRefundPending(storedReference) } returns
                    noOp(succeeded.copy(status = PaymentStatus.REFUND_PENDING))

            assertThrows<ConflictException> { service.refundPaymentForReservation(reservationId) }

            coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
        }

    @Test
    fun `a failed refund can be retried and calls Paystack`() = runTest {
        val failedRefund = pendingPayment.copy(status = PaymentStatus.REFUND_FAILED)
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(
            listOf(failedRefund)
        )
        coEvery { paymentRepository.markRefundPending(storedReference) } returns
                applied(
                    failedRefund.copy(status = PaymentStatus.REFUND_PENDING),
                    from = PaymentStatus.REFUND_FAILED
                )
        coEvery { paystackClient.refundTransaction(storedReference, amount = null) } returns
                RefundResponse(status = true, message = "Refund queued")
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns
                DataResult.Success(failedRefund.copy(status = PaymentStatus.REFUND_PENDING))

        val result = service.refundPaymentForReservation(reservationId)

        assertEquals(PaymentStatus.REFUND_PENDING, result?.status)
        coVerify(exactly = 1) { paystackClient.refundTransaction(storedReference, null) }
    }

    @Test
    fun `a charge success for a reservation cancelled first refunds that payment`() = runTest {
        val succeeded = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        val body =
            """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount}}}"""
        coEvery { paymentRepository.applyChargeResult(any(), any(), any(), any(), any(), any()) } returns
                applied(succeeded)
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } throws
                BadRequestException("Cannot confirm a cancelled reservation")
        coEvery { reservationService.getReservationById(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CANCELLED)
        coEvery { paymentRepository.markRefundPending(storedReference) } returns
                applied(succeeded.copy(status = PaymentStatus.REFUND_PENDING), from = PaymentStatus.SUCCESS)
        coEvery { paystackClient.refundTransaction(storedReference, amount = null) } returns
                RefundResponse(status = true, message = "Refund queued")
        coEvery { paymentRepository.enqueueRefundNotice(storedReference, any()) } returns DataResult.Success(Unit)
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns
                DataResult.Success(succeeded.copy(status = PaymentStatus.REFUND_PENDING))

        service.handleWebhook(body, hmacSha512(body, webhookSecret))

        coVerify(exactly = 1) { paystackClient.refundTransaction(storedReference, null) }
        verify(exactly = 1) {
            paymentEvents.automaticRefund(reservationId, pendingPayment.amount, "reservation_cancelled", "refund_pending")
        }
    }

    @Test
    fun `a transient confirm failure on a still-pending reservation never refunds`() = runTest {
        val body =
            """{"event":"charge.success","data":{"id":99,"reference":"$storedReference","status":"success","amount":${pendingPayment.amount}}}"""
        coEvery { paymentRepository.applyChargeResult(any(), any(), any(), any(), any(), any()) } returns
                applied(pendingPayment.copy(status = PaymentStatus.SUCCESS))
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } throws InternalServerException()
        coEvery { reservationService.getReservationById(reservationId) } returns pendingReservation

        service.handleWebhook(body, hmacSha512(body, webhookSecret))

        coVerify(exactly = 0) { paymentRepository.markRefundPending(any()) }
        coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
    }

    @Test
    fun `a refund already claimed by a concurrent charge result is not reported again`() = runTest {
        val succeeded = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        coEvery { paymentRepository.findPendingPaymentsOlderThan(any(), any()) } returns DataResult.Success(emptyList())
        coEvery { paymentRepository.findUnsettledSuccessfulPayments() } returns
                DataResult.Success(listOf(succeeded))
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } throws
                ConflictException("Cannot confirm a cancelled reservation")
        coEvery { reservationService.getReservationById(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CANCELLED)
        coEvery { paymentRepository.findRefundPendingOlderThan(any()) } returns DataResult.Success(emptyList())
        coEvery { paymentRepository.markRefundPending(storedReference) } returns
                noOp(succeeded.copy(status = PaymentStatus.REFUND_PENDING))

        service.reconcilePendingPayments()

        coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
        verify(exactly = 0) { paymentEvents.automaticRefund(any(), any(), any(), any()) }
    }

    @Test
    fun `reconcilePendingPayments re-confirms SUCCESS payments whose reservation is still pending`() = runTest {
        coEvery { paymentRepository.findPendingPaymentsOlderThan(any(), any()) } returns DataResult.Success(emptyList())
        coEvery { paymentRepository.findUnsettledSuccessfulPayments() } returns
                DataResult.Success(listOf(pendingPayment.copy(status = PaymentStatus.SUCCESS)))
        coEvery { paymentRepository.findRefundPendingOlderThan(any()) } returns DataResult.Success(emptyList())
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CONFIRMED)
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns
                DataResult.Success(listOf(pendingPayment.copy(status = PaymentStatus.SUCCESS)))

        service.reconcilePendingPayments()

        coVerify(exactly = 1) { reservationService.confirmReservationFromPayment(reservationId) }
        coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
    }

    @Test
    fun `a refund claim that fails in the database is not reported as a failed refund`() = runTest {
        val succeeded = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        coEvery { paymentRepository.findPendingPaymentsOlderThan(any(), any()) } returns DataResult.Success(emptyList())
        coEvery { paymentRepository.findRefundPendingOlderThan(any()) } returns DataResult.Success(emptyList())
        coEvery { paymentRepository.findUnsettledSuccessfulPayments() } returns DataResult.Success(listOf(succeeded))
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } throws
                ConflictException("Cannot confirm a cancelled reservation")
        coEvery { reservationService.getReservationById(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CANCELLED)
        coEvery { paymentRepository.markRefundPending(storedReference) } returns DataResult.Failure.UnknownError("deadlock")
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(succeeded)

        service.reconcilePendingPayments()

        coVerify(exactly = 0) { paystackClient.refundTransaction(any(), any()) }
        verify(exactly = 0) { paymentEvents.automaticRefund(any(), any(), any(), any()) }
    }

    private fun hmacSha512(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun applied(p: Payment, from: PaymentStatus = PaymentStatus.PENDING) =
        DataResult.Success(
            PaymentUpdate(
                p,
                result = PaymentUpdate.Result.APPLIED,
                previousStatus = from
            )
        )

    private fun noOp(p: Payment) =
        DataResult.Success(
            PaymentUpdate(
                p,
                result = PaymentUpdate.Result.NO_OP,
                previousStatus = p.status
            )
        )

    private fun rejected(p: Payment, from: PaymentStatus = p.status) =
        DataResult.Success(
            PaymentUpdate(
                p,
                result = PaymentUpdate.Result.REJECTED,
                previousStatus = from
            )
        )
}