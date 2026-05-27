package com.martdev.flickq.features.report.domain.service

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.report.domain.repository.ReportRepository
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.CapacityTotals
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class ReportServiceImplTest {

    @MockK
    private lateinit var repo: ReportRepository

    private val config = PaystackConfig(currency = "NGN")
    private lateinit var service: ReportServiceImpl

    private val from = Instant.parse("2026-05-01T00:00:00Z")
    private val to = Instant.parse("2026-05-31T00:00:00Z")

    @BeforeEach
    fun setup() {
        service = ReportServiceImpl(repo, config)
    }

    @Test
    fun `revenue report folds bucket sums into grand totals`() = runTest {
        coEvery {
            repo.getRevenueBuckets(from, to, ReportBucketGranularity.DAY)
        } returns DataResult.Success(
            listOf(
                RevenueBucket(
                    bucketStart = Instant.parse("2026-05-01T00:00:00Z"),
                    gross = 10_000, refunds = 1_000, net = 9_000, ticketsSold = 3,
                ),
                RevenueBucket(
                    bucketStart = Instant.parse("2026-05-02T00:00:00Z"),
                    gross = 5_000, refunds = 0, net = 5_000, ticketsSold = 1,
                ),
            )
        )

        val report = service.getRevenueReport(from, to, ReportBucketGranularity.DAY)

        assertEquals(15_000L, report.totalGross)
        assertEquals(1_000L, report.totalRefunds)
        assertEquals(14_000L, report.totalNet)
        assertEquals(4L, report.totalTicketsSold)
        assertEquals("NGN", report.currency)
        assertEquals(ReportBucketGranularity.DAY, report.bucket)
    }

    @Test
    fun `revenue report with empty buckets returns zero totals`() = runTest {
        coEvery {
            repo.getRevenueBuckets(from, to, ReportBucketGranularity.DAY)
        } returns DataResult.Success(emptyList())

        val report = service.getRevenueReport(from, to, ReportBucketGranularity.DAY)

        assertEquals(0L, report.totalGross)
        assertEquals(0L, report.totalRefunds)
        assertEquals(0L, report.totalNet)
        assertEquals(0L, report.totalTicketsSold)
        assertEquals(0, report.buckets.size)
    }

    @Test
    fun `revenue report throws BadRequest when from is not before to`() = runTest {
        assertThrows<BadRequestException> {
            service.getRevenueReport(from = to, to = from, bucket = ReportBucketGranularity.DAY)
        }
    }

    @Test
    fun `revenue report throws BadRequest when from equals to`() = runTest {
        assertThrows<BadRequestException> {
            service.getRevenueReport(from = from, to = from, bucket = ReportBucketGranularity.DAY)
        }
    }

    @Test
    fun `capacity report returns avg occupancy 0 when total seats is 0`() = runTest {
        coEvery {
            repo.getCapacityRows(from, to, 10, 0L, null, null)
        } returns DataResult.Success(emptyList())
        coEvery {
            repo.getCapacityTotals(from, to, null, null)
        } returns DataResult.Success(CapacityTotals(0, 0, 0))

        val report = service.getCapacityReport(from, to, 10, 0L, null, null)

        assertEquals(0.0, report.avgOccupancyRate)
        assertEquals(0L, report.totalShowtimes)
    }

    @Test
    fun `capacity report computes avg occupancy as totalBooked over totalTotal`() = runTest {
        coEvery {
            repo.getCapacityRows(from, to, 10, 0L, null, null)
        } returns DataResult.Success(
            listOf(
                CapacityRow(
                    showtimeId = 1, movieId = 1, movieTitle = "M",
                    roomId = 1, roomName = "R",
                    startsAt = from, endsAt = to,
                    seatsTotal = 100, seatsBooked = 50, seatsHeld = 10,
                    seatsAvailable = 40, occupancyRate = 0.5,
                )
            )
        )
        coEvery {
            repo.getCapacityTotals(from, to, null, null)
        } returns DataResult.Success(
            CapacityTotals(
                totalShowtimes = 1,
                totalBooked = 50,
                totalTotal = 100
            )
        )

        val report = service.getCapacityReport(from, to, 10, 0L, null, null)

        assertEquals(0.5, report.avgOccupancyRate)
        assertEquals(1L, report.totalShowtimes)
        assertEquals(50L, report.totalSeatsBooked)
        assertEquals(100L, report.totalSeatsTotal)
    }

    @Test
    fun `capacity report throws BadRequest when from is not before to`() = runTest {
        assertThrows<BadRequestException> {
            service.getCapacityReport(from = to, to = from, limit = 10, offset = 0L, movieId = null, roomId = null)
        }
    }
}