package com.martdev.flickq.feature.booking.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.booking.domain.BookingRepository
import com.martdev.flickq.feature.booking.domain.SeatAvailability
import com.martdev.flickq.feature.booking.domain.SeatMap
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.room.model.Seat

/**
 * In-memory seat maps + reservations used while the app runs on fakes. Seat maps
 * are generated per showtime (8x10 room, a sprinkling of pre-booked seats) and
 * mutated as reservations are made, so re-entering a showtime shows seats taken.
 * Swapped for a Ktor-backed implementation (room dims + available-seats +
 * CreateReservationRequest) when wiring the real API.
 */
class FakeBookingDataSource : BookingRepository {

    private val rows = 8
    private val columns = 10
    private val seatPrice = 3500

    // showtimeId -> (seatId -> status)
    private val seatStatuses = mutableMapOf<Long, MutableMap<Long, SeatStatus>>()
    private val seatMeta = mutableMapOf<Long, List<Seat>>()
    private val reservations = mutableMapOf<Long, Reservation>()
    private var reservationSeq = 1L

    override suspend fun getSeatMap(showtimeId: Long): Result<SeatMap, DataError> {
        ensureGenerated(showtimeId)
        val statuses = seatStatuses.getValue(showtimeId)
        val seats = seatMeta.getValue(showtimeId).map { seat ->
            SeatAvailability(seat = seat, status = statuses.getValue(seat.id))
        }
        return Result.Success(
            SeatMap(
                showtimeId = showtimeId,
                rows = rows,
                columns = columns,
                seatPrice = seatPrice,
                seats = seats
            )
        )
    }

    override suspend fun createReservation(
        showtimeId: Long,
        seatIds: List<Long>
    ): Result<Reservation, DataError> {
        if (seatIds.isEmpty()) return Result.Error(DataError.Network.BAD_REQUEST)
        ensureGenerated(showtimeId)
        val statuses = seatStatuses.getValue(showtimeId)

        val allAvailable = seatIds.all { statuses[it] == SeatStatus.AVAILABLE }
        if (!allAvailable) return Result.Error(DataError.Network.CONFLICT)

        seatIds.forEach { statuses[it] = SeatStatus.BOOKED }

        val reservationId = reservationSeq++
        val reservation = Reservation(
            id = reservationId,
            userId = 1,
            showtimeId = showtimeId,
            status = ReservationStatus.PENDING,
            totalAmount = seatIds.size.toLong() * seatPrice,
            seats = seatIds.map { seatId ->
                ShowtimeSeat(
                    showtimeId = showtimeId,
                    seatId = seatId,
                    reservationId = reservationId,
                    status = SeatStatus.BOOKED
                )
            }
        )
        reservations[reservationId] = reservation
        return Result.Success(reservation)
    }

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        reservations[id]?.let { Result.Success(it) } ?: Result.Error(DataError.Network.NOT_FOUND)

    private fun ensureGenerated(showtimeId: Long) {
        if (seatMeta.containsKey(showtimeId)) return
        val roomId = showtimeId
        val seats = ArrayList<Seat>(rows * columns)
        val statuses = LinkedHashMap<Long, SeatStatus>(rows * columns)
        var index = 0
        for (rowIndex in 0 until rows) {
            val rowLabel = ('A' + rowIndex).toString()
            for (colIndex in 0 until columns) {
                val seatId = showtimeId * 100 + index
                seats.add(
                    Seat(
                        id = seatId,
                        roomId = roomId,
                        rowLabel = rowLabel,
                        seatNumber = colIndex + 1
                    )
                )
                // Deterministic pre-booked sprinkle so the map looks lived-in.
                val preBooked = (showtimeId + index) % 7L == 0L
                statuses[seatId] = if (preBooked) SeatStatus.BOOKED else SeatStatus.AVAILABLE
                index++
            }
        }
        seatMeta[showtimeId] = seats
        seatStatuses[showtimeId] = statuses
    }
}
