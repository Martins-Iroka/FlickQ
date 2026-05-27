package com.martdev.flickq.features.showtime.infrastructure.db.repository

import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeEntity
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.features.showtime.infrastructure.db.table.toShowtime
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.koin.core.annotation.Single

@Single
class ShowtimeRepositoryImpl : ShowtimeRepository {
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
            val updatedShowtime = ShowtimeEntity.findByIdAndUpdate(showtime.id) {
                it.startsAt = showtime.startsAt
                it.endsAt = showtime.endsAt
                it.price = showtime.price
                it.status = showtime.status
            }?.toShowtime() ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

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
            val showtime = ShowtimeEntity.findByIdAndUpdate(id) {
                it.status = status
            }?.toShowtime() ?: return@withSuspendTransaction DataResult.Failure.NotFound(notFoundMessage)

            DataResult.Success(showtime)
        }
    }
}