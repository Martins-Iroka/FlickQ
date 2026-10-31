package com.martdev.flickq.features.payment.domain.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.repository.UserRepositoryImpl
import com.martdev.flickq.features.auth.infrastructure.db.table.UserRefreshTokenTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.features.movie.domain.repository.GenreRepository
import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.features.movie.infrasturcture.repository.GenreRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.repository.MovieRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.tables.GenresTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MovieGenreTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.infrastructure.db.repository.PaymentRepositoryImpl
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse
import com.martdev.flickq.features.payment.observability.PaymentEvents
import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
import com.martdev.flickq.features.reservation.domain.service.ReservationCancellationServiceImpl
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.features.reservation.domain.service.ReservationServiceImpl
import com.martdev.flickq.features.reservation.infrastructure.db.repository.ReservationRepositoryImpl
import com.martdev.flickq.features.reservation.infrastructure.db.repository.ShowtimeSeatRepositoryImpl
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatTable
import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.domain.repository.SeatRepository
import com.martdev.flickq.features.room.infrastructure.db.repository.RoomRepositoryImpl
import com.martdev.flickq.features.room.infrastructure.db.repository.SeatRepositoryImpl
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.features.showtime.infrastructure.db.repository.ShowtimeRepositoryImpl
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.PostgresContainer
import com.martdev.flickq.utils.TestNotifications
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A Paystack charge can land after its reservation stopped being PENDING (the expiry sweep freed the seats),
 * or the confirm can fail for a transient reason. Either way the payment is SUCCESS and must not stay
 * stranded: a cancelled reservation is refunded, a still-pending one is confirmed by the reconciler.
 */
@Testcontainers
class StrandedPaymentSettlementTest {

    companion object {
        private const val SECRET = "sk_test_secret"
        private const val AMOUNT = 1_000_000L
        private lateinit var paymentRepo: PaymentRepository
        private lateinit var reservationRepo: ReservationRepository
        private lateinit var showtimeSeatRepo: ShowtimeSeatRepository
        private lateinit var showtimeRepo: ShowtimeRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var seatRepo: SeatRepository
        private lateinit var movieRepo: MovieRepository
        private lateinit var genreRepo: GenreRepository
        private lateinit var userRepo: UserRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            val enqueuer = TestNotifications.enqueuer()
            paymentRepo = PaymentRepositoryImpl(enqueuer)
            reservationRepo = ReservationRepositoryImpl(enqueuer)
            showtimeSeatRepo = ShowtimeSeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl(enqueuer)
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            movieRepo = MovieRepositoryImpl(enqueuer)
            genreRepo = GenreRepositoryImpl()
            userRepo = UserRepositoryImpl()
        }
    }

    private val paystack = mockk<PaystackClient>()
    private val paymentEvents = mockk<PaymentEvents>(relaxed = true)

    @BeforeEach
    fun clean() {
        transaction {
            NotificationOutboxTable.deleteAll()
            PaymentTable.deleteAll()
            ShowtimeSeatTable.deleteAll()
            ReservationTable.deleteAll()
            ShowtimeTable.deleteAll()
            RoomTable.deleteAll()
            MovieGenreTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserRefreshTokenTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @Test
    fun `a charge success webhook arriving after the expiry sweep refunds the payment`() = runTest {
        val ctx = pendingPaymentForExpiredReservation()
        reservationRepo.cancelExpiredReservation()
        refundAccepted(ctx.reference)

        service().handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(seatsHeldBy(ctx.reservationId)).isEqualTo(0)
        coVerify(exactly = 1) { paystack.refundTransaction(ctx.reference, null) }
        verify(exactly = 1) {
            paymentEvents.automaticRefund(ctx.reservationId, AMOUNT, "reservation_cancelled", "refund_pending")
        }
    }

    @Test
    fun `a verify arriving after the expiry sweep refunds the payment`() = runTest {
        val ctx = pendingPaymentForExpiredReservation()
        reservationRepo.cancelExpiredReservation()
        refundAccepted(ctx.reference)
        coEvery { paystack.verifyTransaction(ctx.reference) } returns verifiedSuccess(ctx.reference)

        val result = service().verifyPayment(ctx.reference, requestingUserId = null)

        assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        coVerify(exactly = 1) { paystack.refundTransaction(ctx.reference, null) }
    }

    @Test
    fun `a rejected refund leaves the payment REFUND_FAILED for the admin retry, and the reconciler leaves it alone`() =
        runTest {
            val ctx = pendingPaymentForExpiredReservation()
            reservationRepo.cancelExpiredReservation()
            coEvery { paystack.refundTransaction(ctx.reference, null) } returns
                    RefundResponse(status = false, message = "Transaction cannot be refunded")
            val service = service()

            service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
            service.reconcilePendingPayments()

            assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_FAILED)
            assertThat(outboxRows(NotificationType.PAYMENT_REFUNDED)).isEmpty()
            coVerify(exactly = 1) { paystack.refundTransaction(ctx.reference, null) }
            verify(exactly = 1) {
                paymentEvents.automaticRefund(ctx.reservationId, AMOUNT, "reservation_cancelled", "refund_failed")
            }
        }

    @Test
    fun `a transient confirm failure is retried by the reconciler and confirms the booking`() = runTest {
        val ctx = pendingPayment()
        val service = service(reservationService = FailingFirstConfirm(realReservationService()))

        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.PENDING)

        service.reconcilePendingPayments()

        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.SUCCESS)
        coVerify(exactly = 0) { paystack.refundTransaction(any(), any()) }
    }

    @Test
    fun `the expiry sweep spares a paid reservation so the reconciler still confirms it`() = runTest {
        val ctx = pendingPayment()
        val service = service(reservationService = FailingFirstConfirm(realReservationService()))

        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
        expire(ctx.reservationId)
        reservationRepo.cancelExpiredReservation()

        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.PENDING)
        assertThat(seatsHeldBy(ctx.reservationId)).isEqualTo(2)

        service.reconcilePendingPayments()

        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.SUCCESS)
        coVerify(exactly = 0) { paystack.refundTransaction(any(), any()) }
    }

    @Test
    fun `a second successful charge on a confirmed booking is refunded and the first is kept`() = runTest {
        val ctx = pendingPayment()
        val second = addPayment(ctx, "${ctx.reference}_2")
        refundAccepted(second)
        val service = service()

        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
        service.handleWebhook(chargeSuccessBody(second), hmacSha512(chargeSuccessBody(second)))

        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(paymentStatus(second)).isEqualTo(PaymentStatus.REFUND_PENDING)
        coVerify(exactly = 1) { paystack.refundTransaction(any(), any()) }
        verify(exactly = 1) {
            paymentEvents.automaticRefund(ctx.reservationId, AMOUNT, "duplicate_charge", "refund_pending")
        }
    }

    @Test
    fun `when the later payment settles first, the earlier one landing second still refunds the later one`() =
        runTest {
            val ctx = pendingPayment()
            val second = addPayment(ctx, "${ctx.reference}_2")
            refundAccepted(second)
            val service = service()

            service.handleWebhook(chargeSuccessBody(second), hmacSha512(chargeSuccessBody(second)))
            service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))

            assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(paymentStatus(second)).isEqualTo(PaymentStatus.REFUND_PENDING)
            coVerify(exactly = 1) { paystack.refundTransaction(second, null) }
        }

    @Test
    fun `an automatic refund pushes the customer once, keyed by payment`() = runTest {
        val ctx = pendingPaymentForExpiredReservation()
        reservationRepo.cancelExpiredReservation()
        refundAccepted(ctx.reference)
        val service = service()

        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
        service.reconcilePendingPayments()

        val rows = outboxRows(NotificationType.PAYMENT_REFUNDED)
        assertThat(rows).hasSize(1)
        assertThat(rows.single()[NotificationOutboxTable.userId]?.value).isEqualTo(ctx.userId)
        assertThat(rows.single()[NotificationOutboxTable.idempotencyKey]).isEqualTo("payment_refunded:${ctx.paymentId}")
    }

    @Test
    fun `an admin-initiated refund sends no refund push of its own`() = runTest {
        val ctx = pendingPayment()
        refundAccepted(ctx.reference)
        val service = service()
        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))

        service.refundPaymentForReservation(ctx.reservationId)

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        assertThat(outboxRows(NotificationType.PAYMENT_REFUNDED)).isEmpty()
    }

    @Test
    fun `concurrent late webhooks and verifies refund a stranded payment exactly once`() = runTest {
        val ctx = pendingPaymentForExpiredReservation()
        reservationRepo.cancelExpiredReservation()
        refundAccepted(ctx.reference)
        coEvery { paystack.verifyTransaction(ctx.reference) } returns verifiedSuccess(ctx.reference)
        val service = service()
        val body = chargeSuccessBody(ctx.reference)

        withContext(Dispatchers.Default) {
            (1..10).map { i ->
                async {
                    runCatching {
                        if (i % 2 == 0) service.handleWebhook(body, hmacSha512(body)) else service.verifyPayment(ctx.reference, null)
                    }
                }
            }.awaitAll()
        }

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        coVerify(exactly = 1) { paystack.refundTransaction(ctx.reference, null) }
        assertThat(outboxRows(NotificationType.PAYMENT_REFUNDED)).hasSize(1)
    }

    @Test
    fun `a refund outcome is still recorded when the caller is cancelled mid-call`() = runTest {
        val ctx = pendingPayment()
        val service = service()
        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
        val paystackCalled = CompletableDeferred<Unit>()
        coEvery { paystack.refundTransaction(ctx.reference, null) } coAnswers {
            paystackCalled.complete(Unit)
            delay(300)
            RefundResponse(status = false, message = "Transaction cannot be refunded")
        }

        val job = launch(Dispatchers.Default) { runCatching { service.refundPaymentForReservation(ctx.reservationId) } }
        paystackCalled.await()
        job.cancelAndJoin()

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_FAILED)
    }

    @Test
    fun `the reconciler marks a refund REFUNDED when Paystack shows the transaction reversed`() = runTest {
        val ctx = refundPendingSince(Clock.System.now().minus(2.hours))
        coEvery { paystack.verifyTransaction(ctx.reference) } returns verified(ctx.reference, "reversed")

        service().reconcilePendingPayments()

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUNDED)
        verify(exactly = 1) { paymentEvents.refundProcessed(ctx.paymentId, AMOUNT) }
        verify(exactly = 0) { paymentEvents.refundsStuck(any()) }
    }

    @Test
    fun `refunds pending for over three days raise one aggregate alert per run and are never resubmitted`() = runTest {
        val stuck = refundPendingSince(Clock.System.now().minus(4.days))
        val abandoned = refundPendingSince(Clock.System.now().minus(20.days))
        refundPendingSince(Clock.System.now().minus(2.hours)).also {
            coEvery { paystack.verifyTransaction(it.reference) } returns verified(it.reference, "success")
        }
        coEvery { paystack.verifyTransaction(stuck.reference) } returns verified(stuck.reference, "success")

        service().reconcilePendingPayments()

        assertThat(paymentStatus(stuck.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        verify(exactly = 1) { paymentEvents.refundsStuck(2) }
        coVerify(exactly = 0) { paystack.verifyTransaction(abandoned.reference) }
        coVerify(exactly = 0) { paystack.refundTransaction(any(), any()) }
    }

    @Test
    fun `an admin cancel of a booking paid twice refunds both charges without the automatic refund push`() = runTest {
        val ctx = pendingPayment()
        val second = addPayment(ctx, "${ctx.reference}_2")
        refundAccepted(ctx.reference)
        refundAccepted(second)
        val service = service()
        service.handleWebhook(chargeSuccessBody(ctx.reference), hmacSha512(chargeSuccessBody(ctx.reference)))
        // The duplicate's own settlement hasn't run yet (e.g. it crashed); the reconciler would get to it later.
        transaction {
            PaymentTable.update({ PaymentTable.reference eq second }) { it[status] = PaymentStatus.SUCCESS }
        }

        ReservationCancellationServiceImpl(realReservationService(), service).cancelByAdmin(ctx.reservationId)

        assertThat(reservationStatus(ctx.reservationId)).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        assertThat(paymentStatus(second)).isEqualTo(PaymentStatus.REFUND_PENDING)
        assertThat(outboxRows(NotificationType.PAYMENT_REFUNDED)).isEmpty()
    }

    @Test
    fun `a recently started refund is left to the refund webhook`() = runTest {
        val ctx = refundPendingSince(Clock.System.now().minus(10.minutes))

        service().reconcilePendingPayments()

        assertThat(paymentStatus(ctx.reference)).isEqualTo(PaymentStatus.REFUND_PENDING)
        coVerify(exactly = 0) { paystack.verifyTransaction(any()) }
    }

    /** Simulates a deadlock (40P01) or dropped connection on the first confirm: UnknownError → InternalServerException. */
    private class FailingFirstConfirm(private val delegate: ReservationService) : ReservationService by delegate {
        private var failed = false
        override suspend fun confirmReservationFromPayment(id: Long): Reservation {
            if (!failed) {
                failed = true
                throw InternalServerException()
            }
            return delegate.confirmReservationFromPayment(id)
        }
    }

    private fun realReservationService(): ReservationService =
        ReservationServiceImpl(reservationRepo, mockk())   // confirm path never touches ShowtimeService

    private fun service(reservationService: ReservationService = realReservationService()) = PaymentServiceImpl(
        paymentRepo,
        reservationService,
        paystack,
        userRepo,
        PaystackConfig(SECRET, "pk_test", "https://api.paystack.co", "", SECRET, "NGN"),
        paymentEvents,
    )

    private fun refundAccepted(reference: String) {
        coEvery { paystack.refundTransaction(reference, null) } returns
                RefundResponse(status = true, message = "Refund queued")
    }

    private fun verifiedSuccess(reference: String) = verified(reference, "success")

    private fun verified(reference: String, status: String) = VerifyResponse(
        status = true, message = "ok",
        data = VerifyResponse.VerifyData(
            id = 1L, status = status, reference = reference, amount = AMOUNT,
            currency = "NGN", paidAt = "2026-10-01T12:00:00Z", gatewayResponse = "Approved"
        ),
    )

    private fun chargeSuccessBody(reference: String) =
        """{"event":"charge.success","data":{"id":1,"reference":"$reference","status":"success","amount":$AMOUNT,"gateway_response":"Approved","paid_at":"2026-10-01T12:00:00Z"}}"""

    private suspend fun paymentStatus(reference: String) =
        (paymentRepo.getPaymentByReference(reference) as DataResult.Success).value.status

    private fun reservationStatus(id: Long) = transaction {
        ReservationTable.selectAll().where { ReservationTable.id eq id }.single()[ReservationTable.status]
    }

    private fun seatsHeldBy(reservationId: Long) = transaction {
        ShowtimeSeatTable.selectAll().where { ShowtimeSeatTable.reservationId eq reservationId }.count()
    }

    private fun outboxRows(type: NotificationType) = transaction {
        NotificationOutboxTable.selectAll().where { NotificationOutboxTable.type eq type }.toList()
    }

    private suspend fun refundPendingSince(since: Instant): Ctx {
        val ctx = pendingPayment()
        transaction {
            PaymentTable.update({ PaymentTable.reference eq ctx.reference }) {
                it[status] = PaymentStatus.REFUND_PENDING
                it[updatedAt] = since
            }
        }
        return ctx
    }

    private suspend fun addPayment(ctx: Ctx, reference: String): String {
        paymentRepo.createPayment(
            Payment(
                reservationId = ctx.reservationId, userId = ctx.userId, reference = reference,
                amount = AMOUNT, status = PaymentStatus.PENDING
            )
        )
        return reference
    }

    private fun expire(reservationId: Long) = transaction {
        ReservationTable.update({ ReservationTable.id eq reservationId }) {
            it[expiresAt] = Clock.System.now().minus(1.minutes)
        }
    }

    private suspend fun pendingPaymentForExpiredReservation(): Ctx = pendingPayment().also { expire(it.reservationId) }

    private suspend fun pendingPayment(): Ctx {
        val n = Random.nextInt(100_000)
        val user = (userRepo.saveUserAndVerificationToken(
            UserData(email = "user_$n@test.com", password = "password"),
            "verify_token_$n",
        ) as DataResult.Success).value
        val genre = (genreRepo.saveGenre(Genre(name = "Genre $n")) as DataResult.Success).value
        val movieId = (movieRepo.createMovie(
            Movie(title = "Movie $n", description = "Description", posterUrl = "poster_url", genres = listOf(genre))
        ) as DataResult.Success).value
        val room = (roomRepo.createRoom(Room(name = "Room $n", rows = 5, columns = 10)) as DataResult.Success).value
        val startsAt = Clock.System.now().plus(1.hours)
        val showtimeId = (showtimeRepo.createShowtime(
            Showtime(movieId = movieId, roomId = room.id, startsAt = startsAt, endsAt = startsAt.plus(2.hours), price = 5000)
        ) as DataResult.Success).value.id
        val seatIds = (seatRepo.createSeats(
            (1..2).map { Seat(roomId = room.id, rowLabel = "A", seatNumber = it) }
        ) as DataResult.Success).value.map { it.id }
        showtimeSeatRepo.populateShowtimeSeats(showtimeId, seatIds)
        val reservation = (reservationRepo.createReservation(
            Reservation(
                userId = user.id, showtimeId = showtimeId, totalAmount = 10_000,
                expiresAt = Clock.System.now().plus(15.minutes)
            ),
            seatIds,
        ) as DataResult.Success).value
        val reference = "ref_stranded_$n"
        val payment = (paymentRepo.createPayment(
            Payment(
                reservationId = reservation.id, userId = user.id, reference = reference,
                amount = AMOUNT, status = PaymentStatus.PENDING
            )
        ) as DataResult.Success).value
        return Ctx(reservationId = reservation.id, userId = user.id, paymentId = payment.id, reference = reference)
    }

    private data class Ctx(val reservationId: Long, val userId: Long, val paymentId: Long, val reference: String)

    private fun hmacSha512(payload: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
