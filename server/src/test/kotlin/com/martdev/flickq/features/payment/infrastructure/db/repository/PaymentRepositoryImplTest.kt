package com.martdev.flickq.features.payment.infrastructure.db.repository

import com.martdev.flickq.auth.model.UserData
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
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
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
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Testcontainers
class PaymentRepositoryImplTest {

    companion object {
        private lateinit var repo: PaymentRepository
        private lateinit var reservationRepo: ReservationRepository
        private lateinit var showtimeSeatRepo: ShowtimeSeatRepository
        private lateinit var showtimeRepo: ShowtimeRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var seatRepo: SeatRepository
        private lateinit var movieRepo: MovieRepository
        private lateinit var genreRepo: GenreRepository
        private lateinit var userRepo: UserRepository
        private val clock = Clock.System.now()

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = PaymentRepositoryImpl()
            reservationRepo = ReservationRepositoryImpl()
            showtimeSeatRepo = ShowtimeSeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            movieRepo = MovieRepositoryImpl()
            genreRepo = GenreRepositoryImpl()
            userRepo = UserRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            PaymentTable.deleteAll()
            ShowtimeSeatTable.deleteAll()
            ReservationTable.deleteAll()
            ShowtimeTable.deleteAll()
            RoomTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserRefreshTokenTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @Test
    fun `createPayment persists all fields and returns Success with generated id`() = runTest {
        val ctx = setupPaymentContext()

        val result = repo.createPayment(
            Payment(
                reservationId = ctx.reservationId,
                userId = ctx.userId,
                reference = "ref_create",
                amount = 500_000L,
                currency = "NGN",
                status = PaymentStatus.PENDING,
                authorizationUrl = "https://paystack.test/pay/abc",
                accessCode = "code_abc",
            )
        )

        assertTrue(result is DataResult.Success, result.toString())
        val saved = result.value
        assertTrue(saved.id > 0)
        assertEquals(ctx.reservationId, saved.reservationId)
        assertEquals(ctx.userId, saved.userId)
        assertEquals("ref_create", saved.reference)
        assertEquals(500_000L, saved.amount)
        assertEquals("NGN", saved.currency)
        assertEquals(PaymentStatus.PENDING, saved.status)
        assertEquals("https://paystack.test/pay/abc", saved.authorizationUrl)
        assertEquals("code_abc", saved.accessCode)
    }

    @Test
    fun `getPaymentByReference returns payment when reference exists`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_lookup", amount = 1000L))

        val result = repo.getPaymentByReference("ref_lookup")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals("ref_lookup", result.value.reference)
        assertEquals(1000L, result.value.amount)
    }

    @Test
    fun `getPaymentByReference returns NotFound when reference is unknown`() = runTest {
        val result = repo.getPaymentByReference("ref_does_not_exist")

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `getPaymentsByReservationId returns payments ordered by createdAt DESC`() = runTest {
        val ctx = setupPaymentContext()
        val older = (repo.createPayment(payment(ctx, reference = "ref_older")) as DataResult.Success).value
        val newer = (repo.createPayment(payment(ctx, reference = "ref_newer")) as DataResult.Success).value
        // Force a deterministic createdAt gap (TIMESTAMP(0) precision could otherwise tie).
        transaction {
            PaymentTable.update({ PaymentTable.id eq older.id }) {
                it[createdAt] = clock.minus(2.hours)
            }
            PaymentTable.update({ PaymentTable.id eq newer.id }) {
                it[createdAt] = clock.minus(1.hours)
            }
        }

        val result = repo.getPaymentsByReservationId(ctx.reservationId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(2, result.value.size)
        assertEquals("ref_newer", result.value[0].reference)
        assertEquals("ref_older", result.value[1].reference)
    }

    @Test
    fun `getPaymentsByReservationId returns empty list when reservation has no payments`() = runTest {
        val ctx = setupPaymentContext()

        val result = repo.getPaymentsByReservationId(ctx.reservationId)

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `getPaymentsByUserId returns the user's payments`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_user_1"))
        repo.createPayment(payment(ctx, reference = "ref_user_2"))

        val result = repo.getPaymentsByUserId(ctx.userId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.userId == ctx.userId })
    }

    @Test
    fun `getPaymentsByUserId returns empty list when user has no payments`() = runTest {
        val result = repo.getPaymentsByUserId(Random.nextLong(1_000_000, 2_000_000))

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `applyChargeResult transitions PENDING to SUCCESS and writes paidAt + gatewayResponse`() = runTest {
        val ctx = setupPaymentContext()
        val created =
            (repo.createPayment(payment(ctx, reference = "ref_charge", amount = 500_000L)) as DataResult.Success).value
        val paidAt = clock.minus(5.minutes)

        val result = repo.applyChargeResult(
            reference = "ref_charge",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "Approved",
            paystackTransactionId = "tx_123",
            paidAt = paidAt,
            amountFromGateway = 500_000L,
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(created.id, result.value.id)
        assertEquals(PaymentStatus.SUCCESS, result.value.status)
        assertEquals("Approved", result.value.gatewayResponse)
        assertEquals("tx_123", result.value.paystackTransactionId)
        assertEquals(paidAt, result.value.paidAt)
    }

    @Test
    fun `applyChargeResult flags amount mismatch as FAILED with explanation`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_mismatch", amount = 500_000L))

        val result = repo.applyChargeResult(
            reference = "ref_mismatch",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "Approved",
            paystackTransactionId = "tx_999",
            paidAt = clock,
            amountFromGateway = 100_000L,
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.FAILED, result.value.status)
        val msg = assertNotNull(result.value.gatewayResponse)
        assertTrue(msg.contains("Amount mismatch"))
        assertTrue(msg.contains("gateway=100000"))
        assertTrue(msg.contains("expected=500000"))
        assertNull(result.value.paidAt, "paidAt must not be set when fraud guard flips to FAILED")
    }

    @Test
    fun `applyChargeResult is idempotent when payment is already SUCCESS`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_idem_success", amount = 500_000L))
        repo.applyChargeResult(
            reference = "ref_idem_success",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "First",
            paystackTransactionId = "tx_first",
            paidAt = clock.minus(10.minutes),
            amountFromGateway = 500_000L,
        )

        val second = repo.applyChargeResult(
            reference = "ref_idem_success",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "Second-should-be-ignored",
            paystackTransactionId = "tx_second",
            paidAt = clock,
            amountFromGateway = 500_000L,
        )

        assertTrue(second is DataResult.Success, second.toString())
        assertEquals(PaymentStatus.SUCCESS, second.value.status)
        assertEquals("First", second.value.gatewayResponse)
        assertEquals("tx_first", second.value.paystackTransactionId)
    }

    @Test
    fun `applyChargeResult is idempotent when payment is already FAILED`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_idem_failed", amount = 500_000L))
        repo.applyChargeResult(
            reference = "ref_idem_failed",
            status = PaymentStatus.FAILED,
            gatewayResponse = "Declined",
            paystackTransactionId = null,
            paidAt = null,
            amountFromGateway = 500_000L,
        )

        val second = repo.applyChargeResult(
            reference = "ref_idem_failed",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "Should-be-ignored",
            paystackTransactionId = "tx_late",
            paidAt = clock,
            amountFromGateway = 500_000L,
        )

        assertTrue(second is DataResult.Success, second.toString())
        assertEquals(PaymentStatus.FAILED, second.value.status)
        assertEquals("Declined", second.value.gatewayResponse)
    }

    @Test
    fun `applyChargeResult returns NotFound when reference does not exist`() = runTest {
        val result = repo.applyChargeResult(
            reference = "ref_nope",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = null,
            paystackTransactionId = null,
            paidAt = clock,
            amountFromGateway = 1000L,
        )

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `applyChargeResult preserves existing paystackTransactionId when null is passed`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_preserve_tx", amount = 1000L))
        repo.applyChargeResult(
            reference = "ref_preserve_tx",
            status = PaymentStatus.PENDING,
            gatewayResponse = null,
            paystackTransactionId = "tx_initial",
            paidAt = null,
            amountFromGateway = 1000L,
        )

        val result = repo.applyChargeResult(
            reference = "ref_preserve_tx",
            status = PaymentStatus.SUCCESS,
            gatewayResponse = "Approved",
            paystackTransactionId = null,
            paidAt = clock,
            amountFromGateway = 1000L,
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals("tx_initial", result.value.paystackTransactionId)
    }

    @Test
    fun `markRefundPending sets status to REFUND_PENDING`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_refund_pending").copy(status = PaymentStatus.SUCCESS))

        val result = repo.markRefundPending("ref_refund_pending")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUND_PENDING, result.value.status)
    }

    @Test
    fun `markRefundPending returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefundPending("ref_unknown")

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `markRefunded sets status to REFUNDED and writes refundedAt`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_refunded").copy(status = PaymentStatus.REFUND_PENDING))
        val refundedAt = clock.minus(1.minutes)

        val result = repo.markRefunded("ref_refunded", refundedAt)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUNDED, result.value.status)
        assertEquals(refundedAt, result.value.refundedAt)
    }

    @Test
    fun `markRefunded returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefunded("ref_unknown", clock)

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `markRefundFailed sets status to REFUND_FAILED and stores gatewayResponse`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_refund_failed").copy(status = PaymentStatus.REFUND_PENDING))

        val result = repo.markRefundFailed("ref_refund_failed", "Refund declined by issuer")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUND_FAILED, result.value.status)
        assertEquals("Refund declined by issuer", result.value.gatewayResponse)
    }

    @Test
    fun `markRefundFailed returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefundFailed("ref_unknown", "boom")

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `findPendingPaymentsOlderThan returns only PENDING payments within the time window`() = runTest {
        val ctx = setupPaymentContext()
        val inWindow = (repo.createPayment(payment(ctx, reference = "ref_in_window")) as DataResult.Success).value
        val tooNew = (repo.createPayment(payment(ctx, reference = "ref_too_new")) as DataResult.Success).value
        val tooOld = (repo.createPayment(payment(ctx, reference = "ref_too_old")) as DataResult.Success).value
        val nonPending = (repo.createPayment(
            payment(ctx, reference = "ref_non_pending").copy(status = PaymentStatus.SUCCESS)
        ) as DataResult.Success).value

        val now = clock
        transaction {
            PaymentTable.update({ PaymentTable.id eq inWindow.id }) { it[createdAt] = now.minus(1.hours) }
            PaymentTable.update({ PaymentTable.id eq tooNew.id }) { it[createdAt] = now.minus(1.minutes) }
            PaymentTable.update({ PaymentTable.id eq tooOld.id }) { it[createdAt] = now.minus(48.hours) }
            PaymentTable.update({ PaymentTable.id eq nonPending.id }) { it[createdAt] = now.minus(1.hours) }
        }

        val result = repo.findPendingPaymentsOlderThan(
            threshold = now.minus(15.minutes),
            giveUpBefore = now.minus(24.hours),
        )

        assertTrue(result is DataResult.Success, result.toString())
        val refs = result.value.map { it.reference }
        assertEquals(listOf("ref_in_window"), refs)
    }

    private data class PaymentContext(val userId: Long, val reservationId: Long)

    private fun payment(ctx: PaymentContext, reference: String, amount: Long = 500_000L) = Payment(
        reservationId = ctx.reservationId,
        userId = ctx.userId,
        reference = reference,
        amount = amount,
        currency = "NGN",
        status = PaymentStatus.PENDING,
    )

    private suspend fun setupPaymentContext(): PaymentContext {
        val n = Random.nextInt(100_000)
        val savedUser = (userRepo.saveUserAndVerificationToken(
            UserData(email = "user_$n@test.com", password = "password"),
            "verify_token_$n",
        ) as DataResult.Success).value

        val savedGenre = (genreRepo.saveGenre(Genre(name = "Genre $n")) as DataResult.Success).value
        val movieId = (movieRepo.createMovie(
            Movie(
                title = "Movie $n",
                description = "Description",
                posterUrl = "poster_url",
                genres = listOf(savedGenre),
            )
        ) as DataResult.Success).value
        val savedRoom =
            (roomRepo.createRoom(Room(name = "Room $n", rows = 5, columns = 10)) as DataResult.Success).value
        val showtimeId = (showtimeRepo.createShowtime(
            Showtime(
                movieId = movieId,
                roomId = savedRoom.id,
                startsAt = clock,
                endsAt = clock.plus(2.hours),
                price = 5000,
            )
        ) as DataResult.Success).value.id

        val seats = (1..2).map { i -> Seat(roomId = savedRoom.id, rowLabel = "A", seatNumber = i) }
        val seatIds = (seatRepo.createSeats(seats) as DataResult.Success).value.map { it.id }
        showtimeSeatRepo.populateShowtimeSeats(showtimeId, seatIds)

        val reservation = (reservationRepo.createReservation(
            Reservation(
                userId = savedUser.id,
                showtimeId = showtimeId,
                totalAmount = 10_000,
                expiresAt = clock.plus(15.minutes),
            ),
            seatIds,
        ) as DataResult.Success).value

        return PaymentContext(userId = savedUser.id, reservationId = reservation.id)
    }
}