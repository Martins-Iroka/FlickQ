package com.martdev.flickq.features.payment.infrastructure.db.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
import com.martdev.flickq.features.payment.domain.repository.PaymentUpdate
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
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.PostgresContainer
import com.martdev.flickq.utils.TestNotifications
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertFalse
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
import kotlin.time.Instant

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
            reservationRepo = ReservationRepositoryImpl(TestNotifications.enqueuer())
            showtimeSeatRepo = ShowtimeSeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl(TestNotifications.enqueuer())
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            movieRepo = MovieRepositoryImpl(TestNotifications.enqueuer())
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
        val older =
            (repo.createPayment(payment(ctx, reference = "ref_older")) as DataResult.Success).value
        val newer =
            (repo.createPayment(payment(ctx, reference = "ref_newer")) as DataResult.Success).value
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
    fun `getPaymentByReservationId returns payment with valid authorization url and status`() =
        runTest {
            val ctx = setupPaymentContext()
            (repo.createPayment(
                payment(
                    ctx,
                    reference = "ref_12345",
                    authorizationUrl = "auth_url_1"
                )
            ))

            val result = repo.getPaymentByReservationId(ctx.reservationId)

            assertTrue(result is DataResult.Success, result.toString())
            assertEquals("auth_url_1", result.value.authorizationUrl)
            assertEquals(PaymentStatus.PENDING, result.value.status)
        }

    @Test
    fun `getPaymentsByReservationId returns empty list when reservation has no payments`() =
        runTest {
            val ctx = setupPaymentContext()

            val result = repo.getPaymentsByReservationId(ctx.reservationId)

            assertTrue(result is DataResult.Success, result.toString())
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun `applyChargeResult transitions PENDING to SUCCESS and writes paidAt + gatewayResponse`() =
        runTest {
            val ctx = setupPaymentContext()
            val created =
                (repo.createPayment(
                    payment(
                        ctx,
                        reference = "ref_charge",
                        amount = 500_000L
                    )
                ) as DataResult.Success).value
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
            assertEquals(created.id, result.value.payment.id)
            assertEquals(PaymentStatus.SUCCESS, result.value.payment.status)
            assertEquals("Approved", result.value.payment.gatewayResponse)
            assertEquals("tx_123", result.value.payment.paystackTransactionId)
            assertEquals(paidAt, result.value.payment.paidAt)
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
        assertEquals(PaymentStatus.FAILED, result.value.payment.status)
        val msg = assertNotNull(result.value.payment.gatewayResponse)
        assertTrue(msg.contains("Amount mismatch"))
        assertTrue(msg.contains("gateway=100000"))
        assertTrue(msg.contains("expected=500000"))
        assertNull(
            result.value.payment.paidAt,
            "paidAt must not be set when fraud guard flips to FAILED"
        )
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
        assertEquals(PaymentStatus.SUCCESS, second.value.payment.status)
        assertEquals("First", second.value.payment.gatewayResponse)
        assertEquals("tx_first", second.value.payment.paystackTransactionId)
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
        assertEquals(PaymentStatus.FAILED, second.value.payment.status)
        assertEquals("Declined", second.value.payment.gatewayResponse)
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
    fun `applyChargeResult preserves existing paystackTransactionId when null is passed`() =
        runTest {
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
            assertEquals("tx_initial", result.value.payment.paystackTransactionId)
        }

    @Test
    fun `markRefundPending sets status to REFUND_PENDING`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_refund_pending"
            ).copy(status = PaymentStatus.SUCCESS)
        )

        val result = repo.markRefundPending("ref_refund_pending")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUND_PENDING, result.value.payment.status)
    }

    @Test
    fun `markRefundPending returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefundPending("ref_unknown")

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `markRefunded sets status to REFUNDED and writes refundedAt`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_refunded"
            ).copy(status = PaymentStatus.REFUND_PENDING)
        )
        val refundedAt = clock.minus(1.minutes)

        val result = repo.markRefunded("ref_refunded", refundedAt)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUNDED, result.value.payment.status)
        assertEquals(refundedAt, result.value.payment.refundedAt)
    }

    @Test
    fun `markRefunded returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefunded("ref_unknown", clock)

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `markRefundFailed sets status to REFUND_FAILED and stores gatewayResponse`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_refund_failed"
            ).copy(status = PaymentStatus.REFUND_PENDING)
        )

        val result = repo.markRefundFailed("ref_refund_failed", "Refund declined by issuer")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentStatus.REFUND_FAILED, result.value.payment.status)
        assertEquals("Refund declined by issuer", result.value.payment.gatewayResponse)
    }

    @Test
    fun `markRefundFailed returns NotFound when reference is unknown`() = runTest {
        val result = repo.markRefundFailed("ref_unknown", "boom")

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `findPendingPaymentsOlderThan returns only PENDING payments within the time window`() =
        runTest {
            val ctx = setupPaymentContext()
            val inWindow = (repo.createPayment(
                payment(
                    ctx,
                    reference = "ref_in_window"
                )
            ) as DataResult.Success).value
            val tooNew = (repo.createPayment(
                payment(
                    ctx,
                    reference = "ref_too_new"
                )
            ) as DataResult.Success).value
            val tooOld = (repo.createPayment(
                payment(
                    ctx,
                    reference = "ref_too_old"
                )
            ) as DataResult.Success).value
            val nonPending = (repo.createPayment(
                payment(ctx, reference = "ref_non_pending").copy(status = PaymentStatus.SUCCESS)
            ) as DataResult.Success).value

            val now = clock
            transaction {
                PaymentTable.update({ PaymentTable.id eq inWindow.id }) {
                    it[createdAt] = now.minus(1.hours)
                }
                PaymentTable.update({ PaymentTable.id eq tooNew.id }) {
                    it[createdAt] = now.minus(1.minutes)
                }
                PaymentTable.update({ PaymentTable.id eq tooOld.id }) {
                    it[createdAt] = now.minus(48.hours)
                }
                PaymentTable.update({ PaymentTable.id eq nonPending.id }) {
                    it[createdAt] = now.minus(1.hours)
                }
            }

            val result = repo.findPendingPaymentsOlderThan(
                threshold = now.minus(15.minutes),
                giveUpBefore = now.minus(24.hours),
            )

            assertTrue(result is DataResult.Success, result.toString())
            val refs = result.value.map { it.reference }
            assertEquals(listOf("ref_in_window"), refs)
        }

    @Test
    fun `findSuccessfulPaymentsWithUnconfirmedReservation returns paid payments whose reservation is not CONFIRMED`() =
        runTest {
            suspend fun paidFor(reservationStatus: ReservationStatus, paymentStatus: PaymentStatus, reference: String) {
                val ctx = setupPaymentContext()
                repo.createPayment(payment(ctx, reference).copy(status = paymentStatus))
                transaction {
                    ReservationTable.update({ ReservationTable.id eq ctx.reservationId }) {
                        it[status] = reservationStatus
                    }
                }
            }
            paidFor(ReservationStatus.PENDING, PaymentStatus.SUCCESS, "ref_paid_pending")
            paidFor(ReservationStatus.CANCELLED, PaymentStatus.SUCCESS, "ref_paid_cancelled")
            paidFor(ReservationStatus.CONFIRMED, PaymentStatus.SUCCESS, "ref_paid_confirmed")
            paidFor(ReservationStatus.CANCELLED, PaymentStatus.PENDING, "ref_unpaid_cancelled")
            paidFor(ReservationStatus.CANCELLED, PaymentStatus.REFUND_PENDING, "ref_refunding_cancelled")
            paidFor(ReservationStatus.CANCELLED, PaymentStatus.REFUND_FAILED, "ref_refund_failed_cancelled")

            val result = repo.findSuccessfulPaymentsWithUnconfirmedReservation()

            assertTrue(result is DataResult.Success, result.toString())
            assertEquals(
                setOf("ref_paid_pending", "ref_paid_cancelled"),
                result.value.map { it.reference }.toSet()
            )
        }

    @Test
    fun `applyChargeResult reports a transition only the first time`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_t", amount = 500_000L))
        suspend fun apply() =
            (repo.applyChargeResult("ref_t", PaymentStatus.SUCCESS,
                "ok",
                "tx",
                clock, 500_000L)
                    as DataResult.Success).value

        assertThat(listOf(apply().transitioned, apply().transitioned)).containsExactly(
            true,
            false
        )
    }

    @Test
    fun `amount mismatch is a transition into FAILED`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_mm", amount = 500_000L))

        val outcome = (repo.applyChargeResult(
            "ref_mm",
            PaymentStatus.SUCCESS,
            "ok",
            "tx",
            clock,
            1L
        ) as DataResult.Success).value

        assertTrue(outcome.transitioned)
        assertThat(outcome.payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    fun `a still-pending gateway answer is not a transition`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_p", amount = 500_000L))

        val outcome = (repo.applyChargeResult(
            "ref_p",
            PaymentStatus.PENDING,
            null,
            null,
            null,
            500_000L
        ) as DataResult.Success).value

        assertFalse(outcome.transitioned)
    }

    @Test
    fun `a replayed charge success on a REFUND_PENDING payment is rejected and changes nothing`() =
        runTest {
            val ctx = setupPaymentContext()
            repo.createPayment(
                payment(
                    ctx,
                    reference = "ref_replay"
                ).copy(status = PaymentStatus.REFUND_PENDING, gatewayResponse = "refund queued")
            )

            val result = repo.applyChargeResult(
                "ref_replay",
                PaymentStatus.SUCCESS,
                "Approved",
                "tx_new",
                clock,
                500_000L
            )

            assertTrue(result is DataResult.Success, result.toString())
            assertEquals(PaymentUpdate.Result.REJECTED, result.value.result)
            assertEquals(PaymentStatus.REFUND_PENDING, result.value.previousStatus)
            val stored = (repo.getPaymentByReference("ref_replay") as DataResult.Success).value
            assertEquals(PaymentStatus.REFUND_PENDING, stored.status)
            assertEquals(
                "refund queued",
                stored.gatewayResponse
            )            // metadata untouched too
            assertNull(stored.paystackTransactionId)
        }

    @Test
    fun `a charge result can never move a pending payment straight into a refund state`() =
        runTest {
            val ctx = setupPaymentContext()
            repo.createPayment(payment(ctx, reference = "ref_pending_refunded"))

            val result = repo.applyChargeResult(
                "ref_pending_refunded",
                PaymentStatus.REFUNDED,
                null,
                null,
                null,
                500_000L
            )

            assertTrue(result is DataResult.Success, result.toString())
            assertEquals(PaymentUpdate.Result.REJECTED, result.value.result)
            assertEquals(
                PaymentStatus.PENDING,
                (repo.getPaymentByReference("ref_pending_refunded") as DataResult.Success).value.status
            )
        }

    @Test
    fun `an abandoned checkout can still succeed`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_abandoned"
            ).copy(status = PaymentStatus.ABANDONED)
        )

        val result = repo.applyChargeResult(
            "ref_abandoned",
            PaymentStatus.SUCCESS,
            "Approved",
            "tx",
            clock,
            500_000L
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.APPLIED, result.value.result)
        assertEquals(PaymentStatus.SUCCESS, result.value.payment.status)
    }

    @Test
    fun `markRefundPending on a payment that never succeeded is rejected`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_not_paid"))

        val result = repo.markRefundPending("ref_not_paid")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.REJECTED, result.value.result)
        assertEquals(PaymentStatus.PENDING, result.value.payment.status)
    }

    @Test
    fun `markRefundPending twice is applied then a no-op`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_claim"
            ).copy(status = PaymentStatus.SUCCESS)
        )

        val first = (repo.markRefundPending("ref_claim") as DataResult.Success).value
        val second = (repo.markRefundPending("ref_claim") as DataResult.Success).value

        assertThat(listOf(first.result, second.result))
            .containsExactly(PaymentUpdate.Result.APPLIED, PaymentUpdate.Result.NO_OP)
    }

    @Test
    fun `ten concurrent refund claims produce exactly one APPLIED`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_race"
            ).copy(status = PaymentStatus.SUCCESS)
        )

        // withSuspendTransaction hops to Dispatchers.IO, so these transactions really overlap.
        val results = (1..10).map { async { repo.markRefundPending("ref_race") } }.awaitAll()

        val outcomes = results.map { (it as DataResult.Success).value.result }
        assertEquals(1, outcomes.count { it == PaymentUpdate.Result.APPLIED })
        assertEquals(9, outcomes.count { it == PaymentUpdate.Result.NO_OP })
    }

    @Test
    fun `a refund can be retried after it failed`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_retry"
            ).copy(status = PaymentStatus.REFUND_FAILED)
        )

        val result = repo.markRefundPending("ref_retry")

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.APPLIED, result.value.result)
        assertEquals(PaymentStatus.REFUND_PENDING, result.value.payment.status)
    }

    @Test
    fun `a late refund failed after refund processed is rejected and REFUNDED stays`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_order"
            ).copy(status = PaymentStatus.REFUND_PENDING)
        )
        repo.markRefunded("ref_order", clock)

        val late = repo.markRefundFailed("ref_order", "stale failure")

        assertTrue(late is DataResult.Success, late.toString())
        assertEquals(PaymentUpdate.Result.REJECTED, late.value.result)
        val stored = (repo.getPaymentByReference("ref_order") as DataResult.Success).value
        assertEquals(PaymentStatus.REFUNDED, stored.status)
        assertTrue(stored.gatewayResponse != "stale failure")
    }

    @Test
    fun `a late refund processed after refund failed wins`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_late_ok"
            ).copy(status = PaymentStatus.REFUND_FAILED)
        )

        val result = repo.markRefunded("ref_late_ok", clock)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.APPLIED, result.value.result)
        assertEquals(PaymentStatus.REFUNDED, result.value.payment.status)
    }

    @Test
    fun `a replayed refund processed keeps the first refundedAt`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_twice"
            ).copy(status = PaymentStatus.REFUND_PENDING)
        )
        val firstAt =
            Instant.fromEpochSeconds(clock.epochSeconds - 60)          // whole seconds: survives TIMESTAMP(0)
        val secondAt = Instant.fromEpochSeconds(clock.epochSeconds)

        repo.markRefunded("ref_twice", firstAt)
        val replay = (repo.markRefunded("ref_twice", secondAt) as DataResult.Success).value

        assertEquals(PaymentUpdate.Result.NO_OP, replay.result)
        assertEquals(
            firstAt,
            (repo.getPaymentByReference("ref_twice") as DataResult.Success).value.refundedAt
        )
    }

    @Test
    fun `refund processed for a pending payment is rejected`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(payment(ctx, reference = "ref_pending_refund"))

        val result = repo.markRefunded("ref_pending_refund", clock)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.REJECTED, result.value.result)
        assertEquals(PaymentStatus.PENDING, result.value.payment.status)
    }

    @Test
    fun `a dashboard refund moves SUCCESS straight to REFUNDED`() = runTest {
        val ctx = setupPaymentContext()
        repo.createPayment(
            payment(
                ctx,
                reference = "ref_dashboard"
            ).copy(status = PaymentStatus.SUCCESS)
        )

        val result = repo.markRefunded("ref_dashboard", clock)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(PaymentUpdate.Result.APPLIED, result.value.result)
    }

    private data class PaymentContext(val userId: Long, val reservationId: Long)

    private fun payment(
        ctx: PaymentContext,
        reference: String,
        amount: Long = 500_000L,
        authorizationUrl: String = ""
    ) = Payment(
        reservationId = ctx.reservationId,
        userId = ctx.userId,
        reference = reference,
        amount = amount,
        currency = "NGN",
        status = PaymentStatus.PENDING,
        authorizationUrl = authorizationUrl
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
            (roomRepo.createRoom(
                Room(
                    name = "Room $n",
                    rows = 5,
                    columns = 10
                )
            ) as DataResult.Success).value
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