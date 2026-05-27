package com.martdev.flickq.features.payment.domain.service

import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.InitializeResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
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
            config
        )
    }

    @Test
    fun `initializePayment converts amount to kobo and persists a PENDING payment`() = runTest {
        coEvery { reservationService.getMyReservationById(reservationId, userId) } returns pendingReservation
        coEvery { paymentRepository.getPaymentsByReservationId(reservationId) } returns DataResult.Success(emptyList())
        coEvery { userRepository.getUserById(userId) } returns DataResult.Success(
            UserData(
                id = userId,
                email = "u@x.io"
            )
        )
        val amountCaptured = slot<Long>()
        coEvery {
            paystackClient.initializeTransaction(
                email = "u@x.io",
                amount = capture(amountCaptured),
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

        assertEquals(500000L, amountCaptured.captured)
        assertEquals(500000L, savedPayment.captured.amount)
        assertEquals(PaymentStatus.PENDING, savedPayment.captured.status)
        assertEquals("ref_returned", result.reference)
        assertEquals("https://paystack.co/pay/abc", result.authorizationUrl)
    }

    @Test
    fun `initializePayment rejects already-paid reservation with Conflict`() = runTest {
        coEvery { reservationService.getMyReservationById(reservationId, userId) } returns pendingReservation
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
    fun `verifyPayment is a no-op when payment is already SUCCESS`() = runTest {
        val confirmed = pendingPayment.copy(status = PaymentStatus.SUCCESS)
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(confirmed)

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.SUCCESS, result.status)
        coVerify(exactly = 0) { paystackClient.verifyTransaction(any()) }
    }

    @Test
    fun `verifyPayment throws Forbidden when requesting user does not own payment`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(pendingPayment)

        assertThrows<ForbiddenException> {
            service.verifyPayment(storedReference, requestingUserId = 999L)
        }
    }

    @Test
    fun `verifyPayment promotes payment to SUCCESS and confirms reservation`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(pendingPayment)
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
        } returns DataResult.Success(pendingPayment.copy(status = PaymentStatus.SUCCESS))
        coEvery { reservationService.confirmReservationFromPayment(reservationId) } returns
                pendingReservation.copy(status = ReservationStatus.CONFIRMED)

        val result = service.verifyPayment(storedReference, requestingUserId = userId)

        assertEquals(PaymentStatus.SUCCESS, result.status)
        coVerify { reservationService.confirmReservationFromPayment(reservationId) }
    }

    @Test
    fun `verifyPayment maps non-success Paystack status to FAILED`() = runTest {
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns DataResult.Success(pendingPayment)
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
        } returns DataResult.Success(pendingPayment.copy(status = PaymentStatus.FAILED))

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
        } returns DataResult.Success(pendingPayment.copy(status = PaymentStatus.SUCCESS))
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
                DataResult.Success(succeeded.copy(status = PaymentStatus.REFUND_PENDING))
        coEvery { paystackClient.refundTransaction(storedReference, amount = null) } returns
                RefundResponse(status = true, message = "Refund queued")
        coEvery { paymentRepository.getPaymentByReference(storedReference) } returns
                DataResult.Success(succeeded.copy(status = PaymentStatus.REFUND_PENDING))

        val result = service.refundPaymentForReservation(reservationId)

        assertEquals(result?.status, PaymentStatus.REFUND_PENDING)
        coVerify { paymentRepository.markRefundPending(storedReference) }
        coVerify { paystackClient.refundTransaction(storedReference, null) }
    }

    private fun hmacSha512(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}