package com.martdev.flickq.features.reservation.infrastructure.db.repository

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationEntity
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.toReservation
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.room.infrastructure.db.table.SeatTable
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationPayment
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single
class ReservationRepositoryImpl : ReservationRepository {
    override suspend fun createReservation(
        reservation: Reservation,
        seatIds: List<Long>
    ): DataResult<Reservation> = withSuspendTransaction {
        // Lock rows to prevent concurrent booking (SELECT FOR UPDATE)
        val lockedSeats = ShowtimeSeatTable
            .selectAll()
            .where {
                (ShowtimeSeatTable.showtimeId eq reservation.showtimeId) and
                        (ShowtimeSeatTable.seatId inList seatIds)
            }
            .forUpdate()
            .toList()

        // All requested seats must exist for this showtime
        if (lockedSeats.size != seatIds.size) {
            rollback()
            return@withSuspendTransaction DataResult.Failure.NotFound(
                "Some seats do not exist for this showtime. Ensure showtime seats have been populated."
            )
        }

        // All request seats must be AVAILABLE
        val unavailableSeats =
            lockedSeats.filter { it[ShowtimeSeatTable.status] != SeatStatus.AVAILABLE }
        if (unavailableSeats.isNotEmpty()) {
            rollback()
            return@withSuspendTransaction DataResult.Failure.Conflict(
                "One or more selected seats are no longer available"
            )
        }

        // Create reservation
        val entity = ReservationEntity.new {
            userId = EntityID(reservation.userId, UserTable)
            showtimeId = EntityID(reservation.showtimeId, ShowtimeTable)
            status = ReservationStatus.PENDING
            totalAmount = reservation.totalAmount
            expiresAt = reservation.expiresAt
        }

        // Hold the seats
        ShowtimeSeatTable.update({
            (ShowtimeSeatTable.showtimeId eq reservation.showtimeId) and
                    (ShowtimeSeatTable.seatId inList seatIds)
        }) {
            it[ShowtimeSeatTable.status] = SeatStatus.HELD
            it[ShowtimeSeatTable.reservationId] = entity.id
        }

        DataResult.Success(entity.toReservation())
    }

    override suspend fun getReservationById(id: Long): DataResult<Reservation> =
        withSuspendTransaction {
            val entity = ReservationEntity.findById(id)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Reservation not found.")
            DataResult.Success(entity.toReservation())
        }

    override suspend fun getReservationsByUserId(userId: Long): DataResult<List<Reservation>> =
        withSuspendTransaction {
            val reservations = ReservationEntity.find {
                ReservationTable.userId eq userId
            }.orderBy(ReservationTable.createdAt to SortOrder.DESC)
                .map { it.toReservation() }

            DataResult.Success(reservations)
        }

    override suspend fun getUserReservationTicket(
        userId: Long,
        status: ReservationStatus,
        limit: Int,
        offset: Long
    ): DataResult<List<ReservationTicket>> {
        val joined = ReservationTable
            .innerJoin(ShowtimeTable)
            .innerJoin(MoviesTable)
            .innerJoin(RoomTable)
            .select(
                ReservationTable.status,
                ReservationTable.totalAmount,
                ReservationTable.expiresAt,
                ShowtimeTable.startsAt,
                ShowtimeTable.endsAt,
                MoviesTable.title,
                MoviesTable.posterUrl,
                RoomTable.name
            ).where {
                (ReservationTable.userId eq userId) and (ReservationTable.status eq status)
            }.orderBy(
                ReservationTable.createdAt to SortOrder.DESC,
                ReservationTable.id to SortOrder.DESC
            ).limit(limit)
            .offset(offset)
            .toList()

        if (joined.isEmpty()) {
            return DataResult.Success(emptyList())
        }

        val reservationIds = joined.map { it[ReservationTable.id] }

        val seatMap = ShowtimeSeatTable
            .innerJoin(SeatTable)
            .select(
                SeatTable.rowLabel,
                SeatTable.seatNumber
            ).where {
                ShowtimeSeatTable.reservationId inList reservationIds
            }.orderBy(
                SeatTable.rowLabel to SortOrder.ASC,
                SeatTable.seatNumber to SortOrder.ASC
            ).toList().groupBy {
                it[ShowtimeSeatTable.reservationId]?.value
            }

        val paymentMap = PaymentTable.select(
            PaymentTable.status,
            PaymentTable.reference,
            PaymentTable.paidAt
        ).where {
            PaymentTable.reservationId inList reservationIds
        }.toList().groupBy {
            it[PaymentTable.reservationId].value
        }
        val reservation = joined.map {
            val reservationId = it[ReservationTable.id].value
            val seats = seatMap[reservationId]?.joinToString(",") { seatRow ->
                val rowLabel = seatRow[SeatTable.rowLabel]
                val seatNumber = seatRow[SeatTable.seatNumber]
                "$rowLabel$seatNumber"
            }.orEmpty()

            val payment = pickLatestPayment(paymentMap[reservationId].orEmpty())

            ReservationTicket(
                status = it[ReservationTable.status],
                totalAmount = it[ReservationTable.totalAmount],
                expiresAt = it[ReservationTable.expiresAt],
                showtimeStartsAt = it[ShowtimeTable.startsAt],
                showtimeEndsAt = it[ShowtimeTable.endsAt],
                movieTitle = it[MoviesTable.title],
                posterUrl = it[MoviesTable.posterUrl],
                roomName = it[RoomTable.name],
                seat = seats,
                payment = payment
            )
        }
        return DataResult.Success(reservation)
    }

    override suspend fun getAllReservations(
        limit: Int,
        offset: Long
    ): DataResult<List<Reservation>> =
        withSuspendTransaction {
            val reservations = ReservationEntity.all()
                .limit(limit)
                .offset(offset)
                .orderBy(
                    (ReservationTable.createdAt to SortOrder.DESC),
                    ReservationTable.id to SortOrder.DESC
                )
                .map { it.toReservation() }
            DataResult.Success(reservations)
        }

    override suspend fun updateReservationStatus(
        id: Long,
        status: ReservationStatus
    ): DataResult<Reservation> = withSuspendTransaction {
        val entity = ReservationEntity.findById(id)
            ?: return@withSuspendTransaction DataResult.Failure.NotFound("Reservation not found.")

        entity.status = status

        when (status) {
            ReservationStatus.CONFIRMED -> {
                ShowtimeSeatTable.update({ ShowtimeSeatTable.reservationId eq entity.id }) {
                    it[ShowtimeSeatTable.status] = SeatStatus.BOOKED
                }
            }

            ReservationStatus.CANCELLED -> {
                ShowtimeSeatTable.update({ ShowtimeSeatTable.reservationId eq entity.id }) {
                    it[ShowtimeSeatTable.status] = SeatStatus.AVAILABLE
                    it[ShowtimeSeatTable.reservationId] = null
                }
            }

            else -> {}
        }

        DataResult.Success(entity.toReservation())
    }

    override suspend fun cancelExpiredReservation(): DataResult<Unit> = withSuspendTransaction {
        val now = Clock.System.now()

        val expired = ReservationEntity.find {
            (ReservationTable.status eq ReservationStatus.PENDING) and
                    (ReservationTable.expiresAt less now)
        }.toList()

        expired.forEach { reservation ->
            reservation.status = ReservationStatus.CANCELLED
            ShowtimeSeatTable.update({ ShowtimeSeatTable.reservationId eq reservation.id }) {
                it[ShowtimeSeatTable.status] = SeatStatus.AVAILABLE
                it[ShowtimeSeatTable.reservationId] = null
            }
        }

        DataResult.Success(Unit)
    }


    private val settledStatuses = setOf(
        PaymentStatus.SUCCESS,
        PaymentStatus.REFUND_PENDING,
        PaymentStatus.REFUNDED,
        PaymentStatus.REFUND_FAILED
    )

    private val paymentRecency = compareBy<ResultRow> (
        { it[PaymentTable.createdAt] },
        { it[PaymentTable.id].value },
    )

    private fun pickLatestPayment(rows: List<ResultRow>): ReservationPayment? {
        val row = rows.filter { it[PaymentTable.status] in settledStatuses }
            .maxWithOrNull(paymentRecency)
            ?: rows.maxWithOrNull(paymentRecency) ?: return null

        return ReservationPayment(
            status = row[PaymentTable.status].name,
            reference = row[PaymentTable.reference],
            paidAt = row[PaymentTable.paidAt]
        )
    }
}