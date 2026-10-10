package com.martdev.flickq.features.notification.infrastructure.db

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
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
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.domain.service.PaymentServiceImpl
import com.martdev.flickq.features.payment.infrastructure.db.repository.PaymentRepositoryImpl
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.payment.infrastructure.paystack.PaystackClient
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse
import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
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
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.PostgresContainer
import com.martdev.flickq.utils.TestNotifications
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Testcontainers
class NotificationEnqueueHooksTest {

    companion object {
        private const val SECRET = "sk_test_secret"
        private lateinit var movieRepo: MovieRepository
        private lateinit var reservationRepo: ReservationRepository
        private lateinit var paymentRepo: PaymentRepository
        private lateinit var genreRepo: GenreRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var seatRepo: SeatRepository
        private lateinit var showtimeRepo: ShowtimeRepository
        private lateinit var showtimeSeatRepo: ShowtimeSeatRepository
        private lateinit var userRepo: UserRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            val enqueuer = TestNotifications.enqueuer()
            movieRepo = MovieRepositoryImpl(enqueuer)
            reservationRepo = ReservationRepositoryImpl(enqueuer)
            paymentRepo = PaymentRepositoryImpl()
            genreRepo = GenreRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl()
            showtimeSeatRepo = ShowtimeSeatRepositoryImpl()
            userRepo = UserRepositoryImpl()
        }
    }

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
    fun `creating a movie enqueues one topic announcement`() = runTest {
        val genre =
            (genreRepo.saveGenre(Genre(name = "G-${Uuid.random()}")) as DataResult.Success).value
        val movieId = (movieRepo.createMovie(
            Movie(
                title = "Dune",
                description = "d",
                posterUrl = "https://cdn.example.test/dune.jpg",
                genres = listOf(genre)
            )
        ) as DataResult.Success).value

        val row = rows(NotificationType.MOVIE_ADDED).single()

        assertThat(row[NotificationOutboxTable.idempotencyKey]).isEqualTo("movie_added:$movieId")
        assertThat(row[NotificationOutboxTable.targetKind]).isEqualTo(NotificationTargetKind.TOPIC)
        assertThat(row[NotificationOutboxTable.topic]).isEqualTo("test-new-movies")
        assertThat(row[NotificationOutboxTable.payload].imageUrl).isEqualTo(
            "https://cdn.example.test/dune.jpg"
        )
        assertThat(row[NotificationOutboxTable.payload].data["deeplink"]).isEqualTo(
            "flickq://movie/$movieId"
        )
    }

    @Test
    fun `a movie whose genres are missing is rolled back with no announcement`() = runTest {
        val result = movieRepo.createMovie(
            Movie(
                title = "X",
                description = "d",
                genres = listOf(Genre(id = 999_999, name = "nope"))
            )
        )

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
        assertThat(rows(NotificationType.MOVIE_ADDED)).isEmpty()
    }

    @Test
    fun `a failing enqueue rolls back the movie too`() = runTest {
        val throwing = object : NotificationEnqueuer {
            override fun enqueueInCurrentTx(event: NotificationEvent): Boolean = error("boom")
            override fun enqueueInCurrentTx(draft: OutboxDraft): Boolean = error("boom")
        }
        val genre = (genreRepo.saveGenre(Genre(name = "G-${Uuid.random()}")) as DataResult.Success).value

        val result = MovieRepositoryImpl(throwing).createMovie(Movie(title = "Dune", description = "d", genres = listOf(genre)))

        assertTrue(result is DataResult.Failure.UnknownError, result.toString())
        assertThat(transaction { MoviesTable.selectAll().count() }).isEqualTo(0L)
    }

    @Test
    fun `holding seats enqueues one user push with seat labels and the hold expiry`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val expiresAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 900)   // whole seconds: TIMESTAMP(0)

        val reservation = hold(ctx, expiresAt)

        val row = rows(NotificationType.RESERVATION_HELD).single()
        assertThat(row[NotificationOutboxTable.idempotencyKey]).isEqualTo("reservation_held:${reservation.id}")
        assertThat(row[NotificationOutboxTable.userId]?.value).isEqualTo(ctx.userId)
        assertThat(row[NotificationOutboxTable.expiresAt]).isEqualTo(expiresAt)
        assertThat(row[NotificationOutboxTable.payload].body)
            .isEqualTo("A1, A2 for ${ctx.movieTitle} are held. Pay within 15 minutes.")
    }

    @Test
    fun `a conflicting hold enqueues nothing`() = runTest {
        val ctx = setupContext(seatCount = 2)
        hold(ctx)

        val second = reservationRepo.createReservation(reservation(ctx), ctx.seatIds)

        assertTrue(second is DataResult.Failure.Conflict, second.toString())
        assertThat(rows(NotificationType.RESERVATION_HELD)).hasSize(1)
    }

    @Test
    fun `confirming a pending reservation enqueues booking_confirmed with the showtime start`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)

        val row = rows(NotificationType.BOOKING_CONFIRMED).single()
        assertThat(row[NotificationOutboxTable.idempotencyKey]).isEqualTo("booking_confirmed:${reservation.id}")
        assertThat(row[NotificationOutboxTable.payload].data["startsAt"]).isEqualTo(ctx.startsAt.toString())
        assertThat(row[NotificationOutboxTable.payload].collapseKey).isEqualTo("reservation-${reservation.id}")
    }

    @Test
    fun `cancelling, or confirming a non-pending reservation, enqueues no booking_confirmed`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CANCELLED)
        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)

        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).isEmpty()
    }

    @Test
    fun `twenty concurrent confirms produce exactly one booking_confirmed row`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)

        val results = (1..20).map {
            async { reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED) }
        }.awaitAll()

        assertThat(results.all { it is DataResult.Success }).isTrue()
        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).hasSize(1)
    }

    @Test
    fun `concurrent webhooks and verifies for one payment enqueue exactly one booking_confirmed row`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val reservation = hold(ctx)
        val ref = "ref_${Uuid.random()}"
        paymentRepo.createPayment(
            Payment(
                reservationId = reservation.id, userId = ctx.userId, reference = ref,
                amount = 1_000_000L, status = PaymentStatus.PENDING
            )
        )
        val paystack = mockk<PaystackClient> {
            coEvery { verifyTransaction(ref) } returns VerifyResponse(
                status = true, message = "ok",
                data = VerifyResponse.VerifyData(id = 1L, status = "success", reference = ref, amount = 1_000_000L,
                    currency = "NGN", paidAt = "2026-10-01T12:00:00Z", gatewayResponse = "Approved"),
            )
        }
        val service = PaymentServiceImpl(
            paymentRepo,
            ReservationServiceImpl(
                reservationRepo,
                mockk()
            ),   // confirm path never touches ShowtimeService
            paystack, userRepo,
            PaystackConfig(SECRET, "pk_test", "https://api.paystack.co", "", SECRET, "NGN"),
            mockk(relaxed = true),
        )
        val body = """{"event":"charge.success","data":{"id":1,"reference":"$ref","status":"success","amount":1000000,"gateway_response":"Approved","paid_at":"2026-10-01T12:00:00Z"}}"""
        val signature = hmacSha512(body, SECRET)

        (1..10).map { i ->
            async { if (i % 2 == 0) service.handleWebhook(body, signature) else service.verifyPayment(ref, null) }
        }.awaitAll()

        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).hasSize(1)
        assertThat((reservationRepo.getReservationById(reservation.id) as DataResult.Success).value.status)
            .isEqualTo(ReservationStatus.CONFIRMED)
    }

    private fun rows(type: NotificationType) = transaction {
        NotificationOutboxTable.selectAll().where { NotificationOutboxTable.type eq type }.toList()
    }

    private data class Ctx(
        val userId: Long,
        val showtimeId: Long,
        val seatIds: List<Long>,
        val movieTitle: String,
        val startsAt: Instant
    )

    private fun reservation(ctx: Ctx, expiresAt: Instant = Clock.System.now() + 15.minutes) =
        Reservation(
            userId = ctx.userId,
            showtimeId = ctx.showtimeId,
            totalAmount = 10_000,
            expiresAt = expiresAt,
            movieTitle = ctx.movieTitle
        )

    private suspend fun hold(ctx: Ctx, expiresAt: Instant = Clock.System.now() + 15.minutes) =
        (reservationRepo.createReservation(
            reservation(ctx, expiresAt),
            ctx.seatIds
        ) as DataResult.Success).value

    private suspend fun setupContext(seatCount: Int): Ctx {
        val n = Uuid.random().toString()
        val user = (userRepo.saveUserAndVerificationToken(
            UserData(email = "u_$n@test.com", password = "password"), "v_$n"
        ) as DataResult.Success).value
        val genre = (genreRepo.saveGenre(Genre(name = "G $n")) as DataResult.Success).value
        val title = "Movie $n"
        val movieId = (movieRepo.createMovie(
            Movie(
                title = title,
                description = "d",
                genres = listOf(genre)
            )
        ) as DataResult.Success).value
        val room = (roomRepo.createRoom(
            Room(
                name = "Room $n",
                rows = 5,
                columns = 10
            )
        ) as DataResult.Success).value
        val startsAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 86_400)
        val showtimeId = (showtimeRepo.createShowtime(
            Showtime(
                movieId = movieId,
                roomId = room.id,
                startsAt = startsAt,
                endsAt = startsAt + 2.hours,
                price = 5000
            )
        ) as DataResult.Success).value.id
        val seatIds = (seatRepo.createSeats((1..seatCount).map {
            Seat(roomId = room.id, rowLabel = "A", seatNumber = it)
        }) as DataResult.Success).value.map { it.id }
        showtimeSeatRepo.populateShowtimeSeats(showtimeId, seatIds)
        return Ctx(user.id, showtimeId, seatIds, title, startsAt)
    }

    private fun hmacSha512(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA512"))
        return mac.doFinal(payload.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }
}