package com.martdev.flickq.features.notification.infrastructure.db

import assertk.assertThat
import assertk.assertions.contains
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
import com.martdev.flickq.showtime.model.ShowtimeStatus
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
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
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
            paymentRepo = PaymentRepositoryImpl(TestNotifications.enqueuer())
            genreRepo = GenreRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl(enqueuer)
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

    // MOVIE_ADDED moved from movie creation to showtime scheduling in 6eaddb0
    // (ShowtimeServiceImpl.createShowtime; covered by ShowtimeServiceImplTest).
    @Test
    fun `creating a movie enqueues no announcement`() = runTest {
        val genre =
            (genreRepo.saveGenre(Genre(name = "G-${Uuid.random()}")) as DataResult.Success).value

        val result = movieRepo.createMovie(
            Movie(
                title = "Dune",
                description = "d",
                posterUrl = "https://cdn.example.test/dune.jpg",
                genres = listOf(genre)
            )
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertThat(rows(NotificationType.MOVIE_ADDED)).isEmpty()
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
    fun `holding seats enqueues one user push with seat labels and the hold expiry`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val expiresAt = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 900)   // whole seconds: TIMESTAMP(0)

        val reservation = hold(ctx, expiresAt)

        val row = rows(NotificationType.RESERVATION_HELD).single()
        assertThat(row[NotificationOutboxTable.idempotencyKey]).isEqualTo("reservation_held:${reservation.id}")
        assertThat(row[NotificationOutboxTable.userId]?.value).isEqualTo(ctx.userId)
        assertThat(row[NotificationOutboxTable.expiresAt]).isEqualTo(expiresAt)
        assertThat(row[NotificationOutboxTable.payload].body)
            .isEqualTo("A1, A2 are held. Pay within 15 minutes.")
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

    @Test
    fun `cancelling a showtime enqueues one push per confirmed reservation only`() = runTest {
        val ctx = setupContext(seatCount = 3)
        val first = holdSeat(ctx, ctx.seatIds[0])
        val second = holdSeat(ctx, ctx.seatIds[1])
        holdSeat(ctx, ctx.seatIds[2])   // stays PENDING
        reservationRepo.updateReservationStatus(first.id, ReservationStatus.CONFIRMED)
        reservationRepo.updateReservationStatus(second.id, ReservationStatus.CONFIRMED)

        val result = showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)

        assertTrue(result is DataResult.Success, result.toString())
        val cancelled = rows(NotificationType.RESERVATION_CANCELLED)
        assertThat(cancelled.map { it[NotificationOutboxTable.idempotencyKey] }.toSet()).isEqualTo(
            setOf("reservation_cancelled:showtime_cancelled:${first.id}", "reservation_cancelled:showtime_cancelled:${second.id}")
        )
        assertThat(cancelled.all { it[NotificationOutboxTable.userId]?.value == ctx.userId }).isTrue()
        assertThat(cancelled.first()[NotificationOutboxTable.payload].body).contains(ctx.movieTitle)
    }

    @Test
    fun `re-cancelling a cancelled showtime or completing one enqueues nothing new`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)
        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)

        showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.COMPLETED)
        assertThat(rows(NotificationType.RESERVATION_CANCELLED)).isEmpty()

        showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)
        transaction { NotificationOutboxTable.deleteAll() }   // a resend would insert a fresh row
        showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)

        assertThat(rows(NotificationType.RESERVATION_CANCELLED)).isEmpty()
    }

    @Test
    fun `paying for a hold after its showtime was cancelled sends the cancellation, not a confirmation`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)
        showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)

        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).isEmpty()
        assertThat(rows(NotificationType.RESERVATION_CANCELLED).single()[NotificationOutboxTable.idempotencyKey])
            .isEqualTo("reservation_cancelled:showtime_cancelled:${reservation.id}")
    }

    @Test
    fun `a payment confirming while a showtime cancel is in flight still gets the cancellation`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val hold = hold(ctx)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        var holderPid = 0
        // A showtime cancel that has locked and flipped the row but not yet committed.
        val cancel = thread {
            transaction {
                ShowtimeTable.selectAll().where { ShowtimeTable.id eq ctx.showtimeId }
                    .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate).single()
                ShowtimeTable.update({ ShowtimeTable.id eq ctx.showtimeId }) { it[status] = ShowtimeStatus.CANCELLED }
                holderPid = exec("SELECT pg_backend_pid()") { it.next(); it.getInt(1) }!!
                locked.countDown()
                release.await()
            }
        }
        locked.await()

        val confirm = async(Dispatchers.IO) { reservationRepo.updateReservationStatus(hold.id, ReservationStatus.CONFIRMED) }
        awaitBlockedBy(holderPid)
        release.countDown()
        cancel.join()
        confirm.await()

        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).isEmpty()
        assertThat(rows(NotificationType.RESERVATION_CANCELLED).single()[NotificationOutboxTable.idempotencyKey])
            .isEqualTo("reservation_cancelled:showtime_cancelled:${hold.id}")
    }

    @Test
    fun `confirming a reservation the expiry sweep already cancelled is a conflict`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val hold = hold(ctx)
        reservationRepo.updateReservationStatus(hold.id, ReservationStatus.CANCELLED)

        val result = reservationRepo.updateReservationStatus(hold.id, ReservationStatus.CONFIRMED)

        assertTrue(result is DataResult.Failure.Conflict, result.toString())
        assertThat((reservationRepo.getReservationById(hold.id) as DataResult.Success).value.status)
            .isEqualTo(ReservationStatus.CANCELLED)
        assertThat(rows(NotificationType.BOOKING_CONFIRMED)).isEmpty()
    }

    @Test
    fun `cancelling a showtime through a full update also notifies confirmed holders`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val hold = hold(ctx)
        reservationRepo.updateReservationStatus(hold.id, ReservationStatus.CONFIRMED)
        val showtime = (showtimeRepo.getShowtimeById(ctx.showtimeId) as DataResult.Success).value

        showtimeRepo.updateShowtime(showtime.copy(status = ShowtimeStatus.CANCELLED))
        showtimeRepo.updateShowtime(showtime.copy(status = ShowtimeStatus.CANCELLED))

        assertThat(rows(NotificationType.RESERVATION_CANCELLED).single()[NotificationOutboxTable.idempotencyKey])
            .isEqualTo("reservation_cancelled:showtime_cancelled:${hold.id}")
    }

    @Test
    fun `the expiry sweep never cancels a reservation whose confirmation committed mid-sweep`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val hold = (reservationRepo.createReservation(
            reservation(ctx, Clock.System.now() - 1.minutes), ctx.seatIds
        ) as DataResult.Success).value
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        var holderPid = 0
        // A payment confirmation that has locked and flipped the row but not yet committed.
        val confirm = thread {
            transaction {
                ReservationTable.selectAll().where { ReservationTable.id eq hold.id }.forUpdate().single()
                ReservationTable.update({ ReservationTable.id eq hold.id }) { it[status] = ReservationStatus.CONFIRMED }
                holderPid = exec("SELECT pg_backend_pid()") { it.next(); it.getInt(1) }!!
                locked.countDown()
                release.await()
            }
        }
        locked.await()

        val sweep = async(Dispatchers.IO) { reservationRepo.cancelExpiredReservation() }
        awaitBlockedBy(holderPid)
        release.countDown()
        confirm.join()
        sweep.await()

        assertThat((reservationRepo.getReservationById(hold.id) as DataResult.Success).value.status)
            .isEqualTo(ReservationStatus.CONFIRMED)
    }

    @Test
    fun `cancelling a showtime with no confirmed holders still succeeds`() = runTest {
        val ctx = setupContext(seatCount = 1)
        hold(ctx)

        val result = showtimeRepo.updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)

        assertTrue(result is DataResult.Success, result.toString())
        assertThat(rows(NotificationType.RESERVATION_CANCELLED)).isEmpty()
    }

    @Test
    fun `a failing enqueue rolls back the showtime cancellation`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)
        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)
        val throwing = object : NotificationEnqueuer {
            override fun enqueueInCurrentTx(event: NotificationEvent): Boolean = error("boom")
            override suspend fun suspendedEnqueueInCurrentTx(event: NotificationEvent): DataResult<Boolean> = error("boom")
            override fun enqueueInCurrentTx(draft: OutboxDraft): Boolean = error("boom")
        }

        val result = ShowtimeRepositoryImpl(throwing).updateShowtimeStatus(ctx.showtimeId, ShowtimeStatus.CANCELLED)

        assertTrue(result is DataResult.Failure.UnknownError, result.toString())
        assertThat((showtimeRepo.getShowtimeById(ctx.showtimeId) as DataResult.Success).value.status)
            .isEqualTo(ShowtimeStatus.SCHEDULED)
    }

    @Test
    fun `admin-cancelling a refunded confirmed reservation enqueues an admin_cancelled push`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)
        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)
        val ref = "ref_${Uuid.random()}"
        paymentRepo.createPayment(
            Payment(
                reservationId = reservation.id, userId = ctx.userId, reference = ref,
                amount = 1_000_000L, status = PaymentStatus.SUCCESS
            )
        )
        paymentRepo.markRefundPending(ref)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CANCELLED)

        val row = rows(NotificationType.RESERVATION_CANCELLED).single()
        assertThat(row[NotificationOutboxTable.idempotencyKey])
            .isEqualTo("reservation_cancelled:admin_cancelled:${reservation.id}")
        assertThat(row[NotificationOutboxTable.payload].title).isEqualTo("Booking cancelled")
    }

    @Test
    fun `cancelling a confirmed reservation with no refund on record promises no refund`() = runTest {
        // e.g. payment confirmed after the cancel's PENDING check, so no refund was issued
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)
        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CONFIRMED)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CANCELLED)

        assertThat(rows(NotificationType.RESERVATION_CANCELLED)).isEmpty()
    }

    @Test
    fun `cancelling a pending reservation enqueues no cancellation push`() = runTest {
        val ctx = setupContext(seatCount = 1)
        val reservation = hold(ctx)

        reservationRepo.updateReservationStatus(reservation.id, ReservationStatus.CANCELLED)

        assertThat(rows(NotificationType.RESERVATION_CANCELLED)).isEmpty()
    }

    /** Waits until a session is blocked by [holderPid], so the interleaving under test really happened. */
    private fun awaitBlockedBy(holderPid: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val blocked = transaction {
                exec("SELECT count(*) FROM pg_stat_activity WHERE $holderPid = ANY(pg_blocking_pids(pid))") {
                    it.next(); it.getInt(1)
                }
            }
            if (blocked != null && blocked > 0) return
            Thread.sleep(20)
        }
        error("No session blocked by pid $holderPid within 10s")
    }

    private suspend fun holdSeat(ctx: Ctx, seatId: Long) =
        (reservationRepo.createReservation(reservation(ctx), listOf(seatId)) as DataResult.Success).value

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