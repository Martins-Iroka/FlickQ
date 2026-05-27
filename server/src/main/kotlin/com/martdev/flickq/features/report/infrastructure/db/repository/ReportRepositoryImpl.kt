package com.martdev.flickq.features.report.infrastructure.db.repository

import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.report.domain.repository.ReportRepository
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatTable
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.CapacityTotals
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Sum
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class ReportRepositoryImpl : ReportRepository {

    // 3-arg date_trunc anchors bucket boundaries in UTC so results don't depend on the session TZ.
    private fun dateTrunc(unit: String, expr: Expression<Instant?>): CustomFunction<Instant?> =
        CustomFunction(
            "date_trunc",
            KotlinInstantColumnType(),
            stringLiteral(unit), expr, stringLiteral("UTC")
        )

    private fun granToUnit(g: ReportBucketGranularity): String = when (g) {
        ReportBucketGranularity.DAY -> "day"
        ReportBucketGranularity.WEEK -> "week"
        ReportBucketGranularity.MONTH -> "month"
    }

    override suspend fun getRevenueBuckets(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity,
    ): DataResult<List<RevenueBucket>> = withSuspendTransaction {
        val unit = granToUnit(bucket)
        val grossBucket = dateTrunc(unit, PaymentTable.paidAt)
        val refundBucket = dateTrunc(unit, PaymentTable.refundedAt)

        val grossSum = PaymentTable.amount.sum()
        val grossRows = PaymentTable
            .select(grossBucket, grossSum)
            .where {
                (PaymentTable.status eq PaymentStatus.SUCCESS) and
                        PaymentTable.paidAt.isNotNull() and
                        (PaymentTable.paidAt greaterEq from) and
                        (PaymentTable.paidAt less to)
            }
            .groupBy(grossBucket)
            .mapNotNull { row ->
                val key = row[grossBucket] ?: return@mapNotNull null
                key to (row[grossSum] ?: 0L)
            }
            .toMap()

        val refundSum = PaymentTable.amount.sum()
        val refundRows = PaymentTable
            .select(refundBucket, refundSum)
            .where {
                (PaymentTable.status eq PaymentStatus.REFUNDED) and
                        PaymentTable.refundedAt.isNotNull() and
                        (PaymentTable.refundedAt greaterEq from) and
                        (PaymentTable.refundedAt less to)
            }
            .groupBy(refundBucket)
            .mapNotNull { row ->
                val key = row[refundBucket] ?: return@mapNotNull null
                key to (row[refundSum] ?: 0L)
            }
            .toMap()

        val ticketCount = ShowtimeSeatTable.id.count()
        val ticketRows = (PaymentTable innerJoin ReservationTable innerJoin ShowtimeSeatTable)
            .select(grossBucket, ticketCount)
            .where {
                (PaymentTable.status eq PaymentStatus.SUCCESS) and
                        PaymentTable.paidAt.isNotNull() and
                        (PaymentTable.paidAt greaterEq from) and
                        (PaymentTable.paidAt less to)
            }
            .groupBy(grossBucket)
            .mapNotNull { row ->
                val key = row[grossBucket] ?: return@mapNotNull null
                key to row[ticketCount]
            }
            .toMap()

        val allKeys = (grossRows.keys + refundRows.keys + ticketRows.keys).distinct().sorted()
        val buckets = allKeys.map { key ->
            val gross = grossRows[key] ?: 0L
            val refunds = refundRows[key] ?: 0L
            RevenueBucket(
                bucketStart = key,
                gross = gross,
                refunds = refunds,
                net = gross - refunds,
                ticketsSold = ticketRows[key] ?: 0L,
            )
        }
        DataResult.Success(buckets)
    }

    override suspend fun getCapacityRows(
        from: Instant,
        to: Instant,
        limit: Int,
        offset: Long,
        movieId: Long?,
        roomId: Long?,
    ): DataResult<List<CapacityRow>> = withSuspendTransaction {
        val booked = Sum(
            Case()
                .When(ShowtimeSeatTable.status eq SeatStatus.BOOKED, intLiteral(1))
                .Else(intLiteral(0)),
            IntegerColumnType(),
        )
        val held = Sum(
            Case()
                .When(ShowtimeSeatTable.status eq SeatStatus.HELD, intLiteral(1))
                .Else(intLiteral(0)),
            IntegerColumnType(),
        )

        val joined: ColumnSet = ShowtimeTable
            .innerJoin(MoviesTable)
            .innerJoin(RoomTable)
            .leftJoin(ShowtimeSeatTable)

        val query = joined
            .select(
                ShowtimeTable.id,
                MoviesTable.id,
                MoviesTable.title,
                RoomTable.id,
                RoomTable.name,
                RoomTable.rows,
                RoomTable.cols,
                ShowtimeTable.startsAt,
                ShowtimeTable.endsAt,
                booked,
                held,
            )
            .where { showtimeRangeFilter(from, to, movieId, roomId) }
            .groupBy(
                ShowtimeTable.id,
                MoviesTable.id,
                MoviesTable.title,
                RoomTable.id,
                RoomTable.name,
                RoomTable.rows,
                RoomTable.cols,
                ShowtimeTable.startsAt,
                ShowtimeTable.endsAt,
            )
            .orderBy(ShowtimeTable.startsAt to SortOrder.ASC)
            .limit(limit)
            .offset(offset)

        val rows = query.map { row ->
            val roomRows = row[RoomTable.rows]
            val roomCols = row[RoomTable.cols]
            val seatsTotal = roomRows * roomCols
            val seatsBooked = row[booked] ?: 0
            val seatsHeld = row[held] ?: 0
            val seatsAvailable = (seatsTotal - seatsBooked - seatsHeld).coerceAtLeast(0)
            val occupancyRate = if (seatsTotal == 0) 0.0 else seatsBooked.toDouble() / seatsTotal

            CapacityRow(
                showtimeId = row[ShowtimeTable.id].value,
                movieId = row[MoviesTable.id].value,
                movieTitle = row[MoviesTable.title],
                roomId = row[RoomTable.id].value,
                roomName = row[RoomTable.name],
                startsAt = row[ShowtimeTable.startsAt],
                endsAt = row[ShowtimeTable.endsAt],
                seatsTotal = seatsTotal,
                seatsBooked = seatsBooked,
                seatsHeld = seatsHeld,
                seatsAvailable = seatsAvailable,
                occupancyRate = occupancyRate,
            )
        }
        DataResult.Success(rows)
    }

    override suspend fun getCapacityTotals(
        from: Instant,
        to: Instant,
        movieId: Long?,
        roomId: Long?,
    ): DataResult<CapacityTotals> = withSuspendTransaction {
        // Per-showtime fetch — avoids double-counting if we joined to seats
        val showtimeRows = (ShowtimeTable innerJoin RoomTable)
            .select(ShowtimeTable.id, RoomTable.rows, RoomTable.cols)
            .where { showtimeRangeFilter(from, to, movieId, roomId) }
            .toList()

        val totalShowtimes = showtimeRows.size.toLong()
        val totalTotal = showtimeRows.sumOf {
            it[RoomTable.rows].toLong() * it[RoomTable.cols].toLong()
        }

        val countCol = ShowtimeSeatTable.id.count()
        val bookedRow = (ShowtimeTable innerJoin ShowtimeSeatTable)
            .select(countCol)
            .where {
                showtimeRangeFilter(from, to, movieId, roomId) and
                        (ShowtimeSeatTable.status eq SeatStatus.BOOKED)
            }
            .firstOrNull()
        val totalBooked = bookedRow?.get(countCol) ?: 0L

        DataResult.Success(CapacityTotals(totalShowtimes, totalBooked, totalTotal))
    }

    private fun showtimeRangeFilter(
        from: Instant,
        to: Instant,
        movieId: Long?,
        roomId: Long?,
    ): Op<Boolean> {
        var cond: Op<Boolean> = (ShowtimeTable.startsAt greaterEq from) and (ShowtimeTable.startsAt less to)
        if (movieId != null) cond = cond and (ShowtimeTable.movieId eq movieId)
        if (roomId != null) cond = cond and (ShowtimeTable.roomId eq roomId)
        return cond
    }
}
