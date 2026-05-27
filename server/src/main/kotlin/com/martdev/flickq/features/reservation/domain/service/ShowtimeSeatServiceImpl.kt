package com.martdev.flickq.features.reservation.domain.service

import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
import com.martdev.flickq.features.room.domain.service.SeatService
import com.martdev.flickq.features.showtime.domain.service.ShowtimeService
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single

@Single
class ShowtimeSeatServiceImpl(
    private val repo: ShowtimeSeatRepository,
    private val showtimeService: ShowtimeService,
    private val seatService: SeatService
) : ShowtimeSeatService {
    override suspend fun populateShowtimeSeats(showtimeId: Long) {
        val showtime = showtimeService.getShowtimeById(showtimeId)
        val seatIds = seatService.getSeatsByRoomId(showtime.roomId).map { it.id }
        repo.populateShowtimeSeats(showtimeId, seatIds).returnValue()
    }

    override suspend fun getAvailableSeats(showtimeId: Long): List<ShowtimeSeat> {
        return repo.getAvailableSeats(showtimeId).returnValue()
    }

    override suspend fun getAllSeatsByShowtime(showtimeId: Long): List<ShowtimeSeat> {
        return repo.getAllSeatsByShowtime(showtimeId).returnValue()
    }
}