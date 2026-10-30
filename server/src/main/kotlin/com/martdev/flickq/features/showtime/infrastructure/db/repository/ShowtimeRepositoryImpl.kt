package com.martdev.flickq.features.showtime.infrastructure.db.repository

import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeEntity
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.features.showtime.infrastructure.db.table.toShowtime
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.core.annotation.Singleton

@Singleton
class ShowtimeRepositoryImpl(
    private val enqueuer: NotificationEnqueuer
) : ShowtimeRepository {
    private val notFoundMessage = "Showtime not found"
    override suspend fun createShowtime(showtime: Showtime): DataResult<Showtime> {
        return withSuspendTransaction {
            val createdST = ShowtimeEntity.new {
                this.movieId = EntityID(showtime.movieId, MoviesTable)
                this.roomId = EntityID(showtime.roomId, RoomTable)
                this.startsAt = showtime.startsAt
                this.endsAt = showtime.endsAt
                this.price = showtime.price
                this.status = showtime.status
            }
            DataResult.Success(createdST.toShowtime())
        }
    }

    override suspend fun getShowtimes(
        limit: Int,
        offset: Long
    ): DataResult<List<Showtime>> {
        return withSuspendTransaction {
            val showTimes = ShowtimeEntity.all()
                .limit(limit)
                .offset(offset)
                .orderBy(ShowtimeTable.createdAt to SortOrder.DESC)
                .map {
                    it.toShowtime()
                }

            DataResult.Success(showTimes)
        }
    }

    override suspend fun getShowtimesByMovieId(movieId: Long): DataResult<List<Showtime>> {
        return withSuspendTransaction {
            val showTimes = ShowtimeEntity.find { ShowtimeTable.movieId eq movieId }.map {
                it.toShowtime()
            }
            DataResult.Success(showTimes)
        }
    }

    override suspend fun getShowtimeById(id: Long): DataResult<Showtime> {
        return withSuspendTransaction {
            val showTime = ShowtimeEntity.findById(id = id)?.toShowtime()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            DataResult.Success(showTime)
        }
    }

    override suspend fun updateShowtime(showtime: Showtime): DataResult<Showtime> {
        return withSuspendTransaction {
            val previous = lockStatus(showtime.id)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            val updatedShowtime = ShowtimeEntity.findByIdAndUpdate(showtime.id) {
                it.startsAt = showtime.startsAt
                it.endsAt = showtime.endsAt
                it.price = showtime.price
                it.status = showtime.status
            }?.toShowtime() ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            if (previous != ShowtimeStatus.CANCELLED && updatedShowtime.status == ShowtimeStatus.CANCELLED) {
                notifyConfirmedHolders(updatedShowtime)
            }

            DataResult.Success(updatedShowtime)
        }
    }

    override suspend fun deleteShowtime(id: Long): DataResult<Int> {
        return withSuspendTransaction {
            val deletedShowtime = ShowtimeTable.deleteWhere {
                ShowtimeTable.id eq id
            }
            if (deletedShowtime <= 0) {
                DataResult.Failure.NotFound(notFoundMessage)
            } else DataResult.Success(deletedShowtime)
        }
    }

    override suspend fun updateShowtimeStatus(
        id: Long,
        status: ShowtimeStatus
    ): DataResult<Showtime> {
        return withSuspendTransaction {
            val previous = lockStatus(id)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            val showtime = ShowtimeEntity.findByIdAndUpdate(id) {
                it.status = status
            }?.toShowtime() ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            if (previous != ShowtimeStatus.CANCELLED && status == ShowtimeStatus.CANCELLED) {
                notifyConfirmedHolders(showtime)
            }

            DataResult.Success(showtime)
        }
    }

    override suspend fun getShowtimeMovieIds(date: LocalDate): DataResult<List<Long>> {
        return withSuspendTransaction {
            val from = date.atStartOfDayIn(TimeZone.UTC)
            val to = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)
            val movieIds = ShowtimeTable
                .select(ShowtimeTable.movieId)
                .where { (ShowtimeTable.status eq ShowtimeStatus.SCHEDULED) and
                        (ShowtimeTable.startsAt greaterEq from) and
                        (ShowtimeTable.startsAt less to) }
                .withDistinct()
                .map { it[ShowtimeTable.movieId].value }
            DataResult.Success(movieIds)
        }
    }

    // Concurrent cancels must not both see SCHEDULED and notify twice, and an in-flight confirmation (FOR SHARE)
    // must land wholly before or after. NO KEY UPDATE still lets reservation inserts' FK checks through.
    private fun lockStatus(id: Long): ShowtimeStatus? = ShowtimeTable
        .select(ShowtimeTable.status)
        .where { ShowtimeTable.id eq id }
        .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
        .firstOrNull()?.get(ShowtimeTable.status)

    private fun notifyConfirmedHolders(showtime: Showtime) {
        val movieTitle = MoviesTable
            .select(MoviesTable.title)
            .where { MoviesTable.id eq showtime.movieId }
            .single()[MoviesTable.title]

        ReservationTable
            .select(ReservationTable.id, ReservationTable.userId)
            .where {
                (ReservationTable.showtimeId eq showtime.id) and
                        (ReservationTable.status eq ReservationStatus.CONFIRMED)
            }
            .forEach {
                enqueuer.enqueueInCurrentTx(
                    NotificationEvent.ReservationCancelled(
                        reservationId = it[ReservationTable.id].value,
                        userId = it[ReservationTable.userId].value,
                        showtimeId = showtime.id,
                        movieTitle = movieTitle,
                        startsAt = showtime.startsAt,
                        reason = NotificationEvent.ReservationCancelled.Reason.SHOWTIME_CANCELLED
                    )
                )
            }
    }
}