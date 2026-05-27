package com.martdev.flickq.features.reservation.infrastructure.db.repository

import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatEntity
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.toShowtimeSeat
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.koin.core.annotation.Single

@Single
class ShowtimeSeatRepositoryImpl : ShowtimeSeatRepository {
    override suspend fun populateShowtimeSeats(
        showtimeId: Long,
        seatIds: List<Long>
    ): DataResult<Unit> = withSuspendTransaction {
        ShowtimeSeatTable.batchInsert(seatIds) { seatId ->
            this[ShowtimeSeatTable.showtimeId] = showtimeId
            this[ShowtimeSeatTable.seatId] = seatId
            this[ShowtimeSeatTable.status] = SeatStatus.AVAILABLE
        }
        DataResult.Success(Unit)
    }

    override suspend fun getAvailableSeats(showtimeId: Long): DataResult<List<ShowtimeSeat>> = withSuspendTransaction {
        val seats = ShowtimeSeatEntity.find {
            (ShowtimeSeatTable.showtimeId eq showtimeId) and
                    (ShowtimeSeatTable.status eq SeatStatus.AVAILABLE)
        }.map { it.toShowtimeSeat() }

        DataResult.Success(seats)
    }

    override suspend fun getAllSeatsByShowtime(showtimeId: Long): DataResult<List<ShowtimeSeat>> =
        withSuspendTransaction {
            val seats = ShowtimeSeatEntity.find {
                ShowtimeSeatTable.showtimeId eq showtimeId
            }.map { it.toShowtimeSeat() }

            DataResult.Success(seats)
        }
}