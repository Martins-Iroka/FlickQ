package com.martdev.flickq.feature.admin.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.report.model.ReportBucketGranularity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Instant

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealAdminReportDataSourceTest {

    private val from = Instant.parse("2026-05-01T00:00:00Z")
    private val to = Instant.parse("2026-06-01T00:00:00Z")

    @Test
    fun `getRevenueReport hits the admin endpoint with from to bucket and maps the envelope`() = runTest {
        var path = ""
        var query = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            query = request.url.encodedQuery
            jsonOk(
                """{"data":{"from":"2026-05-01T00:00:00Z","to":"2026-06-01T00:00:00Z","bucket":"DAY",""" +
                    """"currency":"NGN","buckets":[{"bucket_start":"2026-05-01T00:00:00Z","gross":100,""" +
                    """"refunds":10,"net":90,"tickets_sold":5}],"total_gross":100,"total_refunds":10,""" +
                    """"total_net":90,"total_tickets_sold":5}}"""
            )
        }

        val report = (RealAdminReportDataSource(client).getRevenueReport(from, to, ReportBucketGranularity.DAY)
                as? Result.Success)?.data ?: fail("expected success")
        assertThat(report.bucket).isEqualTo(ReportBucketGranularity.DAY)
        assertThat(report.currency).isEqualTo("NGN")
        assertThat(report.totalNet).isEqualTo(90L)
        assertThat(report.buckets).hasSize(1)
        assertThat(report.buckets[0].ticketsSold).isEqualTo(5L)
        assertThat(path.endsWith("/admin/reports/revenue")).isEqualTo(true)
        assertThat(query.contains("bucket=DAY")).isEqualTo(true)
        assertThat(query.contains("from=")).isEqualTo(true)
    }

    @Test
    fun `getCapacityReport maps rows and omits null movie and room filters`() = runTest {
        var query = ""
        val client = jsonClient { request ->
            query = request.url.encodedQuery
            jsonOk(
                """{"data":{"from":"2026-05-01T00:00:00Z","to":"2026-06-01T00:00:00Z","rows":[""" +
                    """{"showtime_id":1,"movie_id":2,"movie_title":"Neon","room_id":3,"room_name":"S1",""" +
                    """"starts_at":"2026-05-02T13:00:00Z","ends_at":"2026-05-02T15:00:00Z","seats_total":80,""" +
                    """"seats_booked":64,"seats_held":4,"seats_available":12,"occupancy_rate":0.8}],""" +
                    """"total_showtimes":1,"avg_occupancy_rate":0.8,"total_seats_booked":64,"total_seats_total":80}}"""
            )
        }

        val report = (RealAdminReportDataSource(client).getCapacityReport(from, to, limit = 25, offset = 0)
                as? Result.Success)?.data ?: fail("expected success")
        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].movieTitle).isEqualTo("Neon")
        assertThat(report.rows[0].seatsBooked).isEqualTo(64)
        assertThat(report.totalSeatsTotal).isEqualTo(80L)
        assertThat(query.contains("limit=25")).isEqualTo(true)
        assertThat(query.contains("movieId")).isEqualTo(false)
        assertThat(query.contains("roomId")).isEqualTo(false)
    }

    @Test
    fun `a 403 from a non-admin token maps to FORBIDDEN`() = runTest {
        val client = jsonClient { jsonOk("", HttpStatusCode.Forbidden) }

        val result = RealAdminReportDataSource(client).getRevenueReport(from, to)

        val error = (result as? Result.Error)?.error ?: fail("expected error, was $result")
        assertThat(error).isEqualTo(DataError.Network.FORBIDDEN)
    }
}
