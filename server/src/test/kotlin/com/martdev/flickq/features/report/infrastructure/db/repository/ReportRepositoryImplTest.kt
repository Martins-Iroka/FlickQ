package com.martdev.flickq.features.report.infrastructure.db.repository

import com.martdev.features.report.domain.repository.ReportRepository
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
import com.martdev.flickq.features.payment.infrastructure.db.repository.PaymentRepositoryImpl
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
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Testcontainers
class ReportRepositoryImplIntegrationTest {

    companion object {
        private lateinit var reportRepo: ReportRepository
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

        @BeforeAll
        @JvmStatic
        fun connectAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            reportRepo = ReportRepositoryImpl()
            paymentRepo = PaymentRepositoryImpl()
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
    fun `revenue DAY buckets are keyed by paid_at and separate gross from refunds`() = runTest {
        // The starts_at must be aligned to today so the showtime falls in [from, to). Use UTC midnight.
        val day1 = Instant.parse("2026-05-01T00:00:00Z")
        val day2 = Instant.parse("2026-05-02T00:00:00Z")
        val day3 = Instant.parse("2026-05-03T00:00:00Z")
        val ctx = setupContext(seatCount = 4, showtimeStart = day1)

        // Two SUCCESS payments on different days, one REFUNDED payment on day3
        val r1 = createConfirmedReservation(ctx, seatCount = 2, totalAmount = 10_000)
        val r2 = createConfirmedReservation(ctx, seatCount = 1, totalAmount = 5_000)
        val r3 = createConfirmedReservation(ctx, seatCount = 1, totalAmount = 3_000)

        seedPayment(r1.id, ctx.userId, amount = 10_000, status = PaymentStatus.SUCCESS, paidAt = day1.plus(2.hours))
        seedPayment(r2.id, ctx.userId, amount = 5_000, status = PaymentStatus.SUCCESS, paidAt = day2.plus(3.hours))
        seedPayment(
            r3.id, ctx.userId, amount = 3_000,
            status = PaymentStatus.REFUNDED,
            paidAt = day1.plus(1.hours),
            refundedAt = day3.plus(1.hours),
        )

        val result = reportRepo.getRevenueBuckets(
            from = day1,
            to = day3.plus(1.days),
            bucket = ReportBucketGranularity.DAY,
        )

        assertTrue(result is DataResult.Success, result.toString())
        val buckets = result.value
        // 3 distinct bucket keys: day1 (gross + tickets), day2 (gross + tickets), day3 (refund)
        assertEquals(3, buckets.size)

        val byKey = buckets.associateBy { it.bucketStart }
        assertEquals(10_000L, byKey[day1]?.gross)
        assertEquals(2L, byKey[day1]?.ticketsSold)
        assertEquals(5_000L, byKey[day2]?.gross)
        assertEquals(1L, byKey[day2]?.ticketsSold)
        assertEquals(3_000L, byKey[day3]?.refunds)
        assertEquals(0L, byKey[day3]?.gross)
    }

    @Test
    fun `revenue MONTH bucket collapses multiple days into one`() = runTest {
        val day1 = Instant.parse("2026-05-01T00:00:00Z")
        val day2 = Instant.parse("2026-05-15T00:00:00Z")
        val month1 = Instant.parse("2026-05-01T00:00:00Z")
        val ctx = setupContext(seatCount = 3, showtimeStart = day1)

        val r1 = createConfirmedReservation(ctx, seatCount = 2, totalAmount = 10_000)
        val r2 = createConfirmedReservation(ctx, seatCount = 1, totalAmount = 5_000)

        seedPayment(r1.id, ctx.userId, amount = 10_000, status = PaymentStatus.SUCCESS, paidAt = day1.plus(2.hours))
        seedPayment(r2.id, ctx.userId, amount = 5_000, status = PaymentStatus.SUCCESS, paidAt = day2.plus(3.hours))

        val result = reportRepo.getRevenueBuckets(
            from = day1,
            to = Instant.parse("2026-06-01T00:00:00Z"),
            bucket = ReportBucketGranularity.MONTH,
        )

        assertTrue(result is DataResult.Success, result.toString())
        val buckets = result.value
        assertEquals(1, buckets.size)
        assertEquals(month1, buckets[0].bucketStart)
        assertEquals(15_000L, buckets[0].gross)
        assertEquals(3L, buckets[0].ticketsSold)
    }

    @Test
    fun `revenue excludes payments outside the date range`() = runTest {
        val ctx = setupContext(seatCount = 2, showtimeStart = Instant.parse("2026-05-01T00:00:00Z"))
        val r1 = createConfirmedReservation(ctx, seatCount = 1, totalAmount = 5_000)

        // Paid before the window
        seedPayment(
            r1.id, ctx.userId, amount = 5_000, status = PaymentStatus.SUCCESS,
            paidAt = Instant.parse("2026-04-25T00:00:00Z"),
        )

        val result = reportRepo.getRevenueBuckets(
            from = Instant.parse("2026-05-01T00:00:00Z"),
            to = Instant.parse("2026-05-31T00:00:00Z"),
            bucket = ReportBucketGranularity.DAY,
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `capacity rows return correct seats counts for in-range showtime`() = runTest {
        val showtimeStart = Instant.parse("2026-05-10T18:00:00Z")
        val ctx = setupContext(seatCount = 4, showtimeStart = showtimeStart, roomRows = 4, roomCols = 4)

        // Confirm 2 (booked), hold 1, leave the rest available
        createConfirmedReservation(ctx, seatCount = 2, totalAmount = 10_000)
        createPendingReservation(ctx, seatCount = 1, totalAmount = 5_000)

        val result = reportRepo.getCapacityRows(
            from = Instant.parse("2026-05-10T00:00:00Z"),
            to = Instant.parse("2026-05-11T00:00:00Z"),
            limit = 10,
            offset = 0L,
            movieId = null,
            roomId = null,
        )

        assertTrue(result is DataResult.Success, result.toString())
        val rows = result.value
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(ctx.showtimeId, row.showtimeId)
        assertEquals(16, row.seatsTotal) // 4 rows × 4 cols
        assertEquals(2, row.seatsBooked)
        assertEquals(1, row.seatsHeld)
        assertEquals(13, row.seatsAvailable)
        assertEquals(2.0 / 16.0, row.occupancyRate, "occupancy_rate")
    }

    @Test
    fun `capacity rows filter out showtimes outside range`() = runTest {
        val inRange = Instant.parse("2026-05-10T18:00:00Z")
        val outOfRange = Instant.parse("2026-06-10T18:00:00Z")
        setupContext(seatCount = 2, showtimeStart = inRange)
        setupContext(seatCount = 2, showtimeStart = outOfRange)

        val result = reportRepo.getCapacityRows(
            from = Instant.parse("2026-05-01T00:00:00Z"),
            to = Instant.parse("2026-06-01T00:00:00Z"),
            limit = 10,
            offset = 0L,
            movieId = null,
            roomId = null,
        )

        assertTrue(result is DataResult.Success, result.toString())
        val rows = result.value
        assertEquals(1, rows.size)
        assertEquals(inRange, rows[0].startsAt)
    }

    @Test
    fun `capacity totals match aggregated capacity rows`() = runTest {
        val start1 = Instant.parse("2026-05-10T18:00:00Z")
        val start2 = Instant.parse("2026-05-11T18:00:00Z")
        val ctx1 = setupContext(seatCount = 4, showtimeStart = start1, roomRows = 4, roomCols = 4)
        val ctx2 = setupContext(seatCount = 4, showtimeStart = start2, roomRows = 4, roomCols = 4)

        createConfirmedReservation(ctx1, seatCount = 2, totalAmount = 10_000)
        createConfirmedReservation(ctx2, seatCount = 3, totalAmount = 15_000)

        val from = Instant.parse("2026-05-10T00:00:00Z")
        val to = Instant.parse("2026-05-12T00:00:00Z")

        val rowsResult = reportRepo.getCapacityRows(from, to, limit = 100, offset = 0L, movieId = null, roomId = null)
        val totalsResult = reportRepo.getCapacityTotals(from, to, movieId = null, roomId = null)

        assertTrue(rowsResult is DataResult.Success && totalsResult is DataResult.Success)
        val rows = rowsResult.value
        val totals = totalsResult.value

        assertEquals(rows.size.toLong(), totals.totalShowtimes)
        assertEquals(rows.sumOf { it.seatsBooked.toLong() }, totals.totalBooked)
        assertEquals(rows.sumOf { it.seatsTotal.toLong() }, totals.totalTotal)
    }

    @Test
    fun `capacity rows filter by movieId and roomId`() = runTest {
        val start = Instant.parse("2026-05-10T18:00:00Z")
        val ctx1 = setupContext(seatCount = 2, showtimeStart = start)
        val ctx2 = setupContext(seatCount = 2, showtimeStart = start.plus(3.hours))

        val from = Instant.parse("2026-05-10T00:00:00Z")
        val to = Instant.parse("2026-05-11T00:00:00Z")

        val filtered = reportRepo.getCapacityRows(
            from, to, limit = 10, offset = 0L,
            movieId = ctx1.movieId,
            roomId = ctx1.roomId,
        )

        assertTrue(filtered is DataResult.Success, filtered.toString())
        val rows = filtered.value
        assertEquals(1, rows.size)
        assertEquals(ctx1.showtimeId, rows[0].showtimeId)
        // ctx2 is excluded
        assertTrue(rows.none { it.showtimeId == ctx2.showtimeId })
    }

    private data class TestContext(
        val userId: Long,
        val movieId: Long,
        val roomId: Long,
        val showtimeId: Long,
        val seatIds: List<Long>,
    )

    private suspend fun setupContext(
        seatCount: Int,
        showtimeStart: Instant,
        roomRows: Int = 5,
        roomCols: Int = 10,
    ): TestContext {
        val n = Random.nextInt(1_000_000)
        val user = UserData(email = "user_$n@test.com", password = "password")
        val savedUser = (userRepo.saveUserAndVerificationToken(user, "verify_$n") as DataResult.Success).value

        val genre = Genre(name = "Genre $n")
        val savedGenre = (genreRepo.saveGenre(genre) as DataResult.Success).value
        val movie = Movie(
            title = "Movie $n",
            description = "Description",
            posterUrl = "poster_url",
            genres = listOf(savedGenre),
        )
        val movieId = (movieRepo.createMovie(movie) as DataResult.Success).value
        val savedRoom = (roomRepo.createRoom(
            Room(name = "Room $n", rows = roomRows, columns = roomCols)
        ) as DataResult.Success).value
        val showtime = Showtime(
            movieId = movieId,
            roomId = savedRoom.id,
            startsAt = showtimeStart,
            endsAt = showtimeStart.plus(2.hours),
            price = 5000,
        )
        val showtimeId = (showtimeRepo.createShowtime(showtime) as DataResult.Success).value.id

        val seats = (1..seatCount).map { i ->
            Seat(
                roomId = savedRoom.id,
                rowLabel = "A",
                seatNumber = i
            )
        }
        val seatIds = (seatRepo.createSeats(seats) as DataResult.Success).value.map { it.id }
        showtimeSeatRepo.populateShowtimeSeats(showtimeId, seatIds)

        return TestContext(savedUser.id, movieId, savedRoom.id, showtimeId, seatIds)
    }

    private val availableSeatIdsTaken = mutableMapOf<Long, MutableSet<Long>>()

    private suspend fun createConfirmedReservation(
        ctx: TestContext,
        seatCount: Int,
        totalAmount: Long,
    ): Reservation {
        val r = createReservationFor(ctx, seatCount, totalAmount, status = ReservationStatus.CONFIRMED)
        // Update seats to BOOKED
        transaction {
            ShowtimeSeatTable.update({
                ShowtimeSeatTable.reservationId eq r.id
            }) {
                it[status] = SeatStatus.BOOKED
            }
        }
        return r
    }

    private suspend fun createPendingReservation(
        ctx: TestContext,
        seatCount: Int,
        totalAmount: Long,
    ): Reservation = createReservationFor(ctx, seatCount, totalAmount, status = ReservationStatus.PENDING)

    private suspend fun createReservationFor(
        ctx: TestContext,
        seatCount: Int,
        totalAmount: Long,
        status: ReservationStatus,
    ): Reservation {
        val taken = availableSeatIdsTaken.getOrPut(ctx.showtimeId) { mutableSetOf() }
        val available = ctx.seatIds.filterNot { it in taken }.take(seatCount)
        require(available.size == seatCount) { "Not enough free seats in test setup" }
        taken.addAll(available)

        val now = Instant.parse("2026-05-01T00:00:00Z")
        val res = (reservationRepo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = totalAmount,
                expiresAt = now.plus(15.minutes),
            ),
            available,
        ) as DataResult.Success).value

        if (status != ReservationStatus.PENDING) {
            transaction {
                ReservationTable.update({ ReservationTable.id eq res.id }) {
                    it[ReservationTable.status] = status
                }
            }
        }
        return res
    }

    private suspend fun seedPayment(
        reservationId: Long,
        userId: Long,
        amount: Long,
        status: PaymentStatus,
        paidAt: Instant?,
        refundedAt: Instant? = null,
    ): Payment {
        val n = Random.nextInt(1_000_000)
        val payment = (paymentRepo.createPayment(
            Payment(
                reservationId = reservationId,
                userId = userId,
                reference = "ref_$n",
                amount = amount,
                status = status,
                paidAt = paidAt,
                refundedAt = refundedAt,
            )
        ) as DataResult.Success).value
        return payment
    }
}