package com.martdev.flickq.features.payment.domain.service

import assertk.assertThat
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
import kotlinx.coroutines.test.runTest
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
            paymentRepo = PaymentRepositoryImpl()
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
        verify(exactly = 1) { paymentEvents.strandedPaymentRefund(ctx.reservationId, AMOUNT, "refund_pending") }
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
            coVerify(exactly = 1) { paystack.refundTransaction(ctx.reference, null) }
            verify(exactly = 1) { paymentEvents.strandedPaymentRefund(ctx.reservationId, AMOUNT, "refund_failed") }
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

    private fun verifiedSuccess(reference: String) = VerifyResponse(
        status = true, message = "ok",
        data = VerifyResponse.VerifyData(
            id = 1L, status = "success", reference = reference, amount = AMOUNT,
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
        paymentRepo.createPayment(
            Payment(
                reservationId = reservation.id, userId = user.id, reference = reference,
                amount = AMOUNT, status = PaymentStatus.PENDING
            )
        )
        return Ctx(reservationId = reservation.id, reference = reference)
    }

    private data class Ctx(val reservationId: Long, val reference: String)

    private fun hmacSha512(payload: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
