package com.martdev.flickq.feature.admin.presentation.logic.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

data class AdminReportsState(
    val isLoading: Boolean = true,
    val revenue: RevenueReport? = null,
    val capacity: CapacityReport? = null,
    val error: UiText? = null,
)

sealed interface AdminReportsAction {
    data object OnRetry : AdminReportsAction
}

/** Loads the revenue + capacity reports for the trailing 30 days when the dashboard opens. */
class AdminReportsViewModel(
    private val reportRepository: AdminReportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReportsState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: AdminReportsAction) {
        when (action) {
            AdminReportsAction.OnRetry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val to = Clock.System.now()
            val from = to - 30.days

            reportRepository.getRevenueReport(from, to, ReportBucketGranularity.DAY)
                .onSuccess { revenue ->
                    reportRepository.getCapacityReport(from, to)
                        .onSuccess { capacity ->
                            _state.update {
                                it.copy(isLoading = false, revenue = revenue, capacity = capacity)
                            }
                        }
                        .onFailure { error, message ->
                            _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                        }
                }
                .onFailure { error, message ->
                    _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }
}
