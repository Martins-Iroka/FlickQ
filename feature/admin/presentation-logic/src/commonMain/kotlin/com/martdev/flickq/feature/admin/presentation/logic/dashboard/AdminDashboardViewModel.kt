package com.martdev.flickq.feature.admin.presentation.logic.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import com.martdev.flickq.report.model.ReportBucketGranularity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A single "Upcoming Showtimes Today" row, already reduced to display-ready primitives. */
data class UpcomingShowtimeItem(
    val movieTitle: String,
    val roomName: String,
    val startsAt: Instant,
    val occupancyPct: Int,
    val seatsBooked: Int,
    val seatsTotal: Int,
)

data class AdminDashboardState(
    val isLoading: Boolean = true,
    val currency: String = "",
    val totalNetRevenue: Long = 0,
    val ticketsSold: Long = 0,
    val activeShowtimesToday: Int = 0,
    val upcomingToday: List<UpcomingShowtimeItem> = emptyList(),
    val error: UiText? = null,
)

sealed interface AdminDashboardAction {
    data object OnRetry : AdminDashboardAction
}

/**
 * Composes the dashboard overview from the existing `admin/reports` endpoints — no dedicated
 * backend aggregation. Two independent requests run in parallel: the trailing-30-days revenue
 * report (net revenue + tickets sold) and today's capacity report (the showtimes scheduled for the
 * current UTC day, which drive the "Active Showtimes" stat and the "Upcoming Showtimes Today" list).
 */
class AdminDashboardViewModel(
    private val reportRepository: AdminReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: AdminDashboardAction) {
        when (action) {
            AdminDashboardAction.OnRetry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val now = Clock.System.now()
            val from = now - 30.days
            // UTC wall-clock day bounds, matching how the rest of the admin UI renders instants.
            val today = now.toString().take(10)
            val startOfToday = Instant.parse("${today}T00:00:00Z")
            val endOfToday = Instant.parse("${today}T23:59:59Z")

            // The two reports are independent — fire both, then await, so latency is one round-trip.
            val revenueDeferred = async { reportRepository.getRevenueReport(from, now, ReportBucketGranularity.DAY) }
            val capacityDeferred = async { reportRepository.getCapacityReport(startOfToday, endOfToday) }

            revenueDeferred.await()
                .onSuccess { revenue ->
                    capacityDeferred.await()
                        .onSuccess { todayCapacity ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    currency = revenue.currency,
                                    totalNetRevenue = revenue.totalNet / 100,
                                    ticketsSold = revenue.totalTicketsSold,
                                    activeShowtimesToday = todayCapacity.totalShowtimes.toInt(),
                                    upcomingToday = todayCapacity.rows
                                        .sortedBy { row -> row.startsAt }
                                        .map { row ->
                                            UpcomingShowtimeItem(
                                                movieTitle = row.movieTitle,
                                                roomName = row.roomName,
                                                startsAt = row.startsAt,
                                                occupancyPct = (row.occupancyRate * 100).roundToInt(),
                                                seatsBooked = row.seatsBooked,
                                                seatsTotal = row.seatsTotal,
                                            )
                                        },
                                )
                            }
                        }
                        .onFailure { error, message -> fail(error, message) }
                }
                .onFailure { error, message -> fail(error, message) }
        }
    }

    private fun fail(error: DataError, message: String?) {
        _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
    }
}
