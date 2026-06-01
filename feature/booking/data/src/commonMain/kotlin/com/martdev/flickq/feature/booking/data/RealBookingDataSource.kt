package com.martdev.flickq.feature.booking.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.core.data.postData
import com.martdev.flickq.feature.booking.domain.BookingRepository
import com.martdev.flickq.feature.booking.domain.SeatAvailability
import com.martdev.flickq.feature.booking.domain.SeatMap
import com.martdev.flickq.reservation.CreateReservationRequest
import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.ShowtimeSeatDTO
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.showtime.ShowtimeDTO
import io.ktor.client.HttpClient

/**
 * Ktor-backed [BookingRepository]. The seat map has no single endpoint — it is assembled
 * from four calls: the showtime (for its room + price), the room (dimensions), the room's
 * full seat list, and the showtime's available-seat list. Any room seat absent from the
 * available list is rendered occupied. Used when
 * [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false.
 */
class RealBookingDataSource(
    private val client: HttpClient
) : BookingRepository {

    override suspend fun getSeatMap(showtimeId: Long): Result<SeatMap, DataError> {
        val showtime = when (val r = client.getData<ShowtimeDTO>("/showtime/get-showtime-by-id/$showtimeId")) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        val room = when (val r = client.getData<RoomDTO>("/room/get-room-by-id/${showtime.roomId}")) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        val seats = when (val r = client.getData<List<SeatDTO>>("/seat/get-seats-by-room-id/${showtime.roomId}")) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        val available = when (val r = client.getData<List<ShowtimeSeatDTO>>("/reservation/available-seats/$showtimeId")) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }

        val availableSeatIds = available.mapTo(HashSet()) { it.seatId }
        val seatAvailability = seats.map { dto ->
            val seat = dto.toSeat()
            val status = if (seat.id in availableSeatIds) SeatStatus.AVAILABLE else SeatStatus.BOOKED
            SeatAvailability(seat = seat, status = status)
        }

        return Result.Success(
            SeatMap(
                showtimeId = showtimeId,
                rows = room.rows,
                columns = room.columns,
                seatPrice = showtime.price,
                seats = seatAvailability
            )
        )
    }

    override suspend fun createReservation(
        showtimeId: Long,
        seatIds: List<Long>
    ): Result<Reservation, DataError> {
        if (seatIds.isEmpty()) return Result.Error(DataError.Network.BAD_REQUEST)
        return client.postData<CreateReservationRequest, ReservationDTO>(
            "/reservation/create",
            CreateReservationRequest(showtimeId = showtimeId, seatIds = seatIds)
        ).map { it.toReservation() }
    }

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        client.getData<ReservationDTO>("/reservation/$id")
            .map { it.toReservation() }
}
