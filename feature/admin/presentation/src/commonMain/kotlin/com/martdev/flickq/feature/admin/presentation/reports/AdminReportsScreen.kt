package com.martdev.flickq.feature.admin.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.RevenueReport
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun AdminReportsRoot(
    viewModel: AdminReportsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminReportsScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun AdminReportsScreen(
    state: AdminReportsState,
    onAction: (AdminReportsAction) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(RoomBackgroundBrush)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = FlickQColors.Gold,
                modifier = Modifier.align(Alignment.Center)
            )

            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Couldn't load reports", color = FlickQColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = state.error.asString(), color = FlickQColors.Error, modifier = Modifier.padding(top = 8.dp))
                FlickQButton(
                    text = "Retry",
                    onClick = { onAction(AdminReportsAction.OnRetry) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Dashboard",
                        color = FlickQColors.Gold,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                state.revenue?.let { revenue -> item { RevenueCard(revenue) } }
                state.capacity?.let { capacity ->
                    item { CapacityCard(capacity) }
                    item {
                        Text(
                            text = "Showtime occupancy",
                            color = FlickQColors.GoldHighlight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(capacity.rows) { row -> CapacityRowItem(row) }
                }
            }
        }
    }
}

@Composable
private fun RevenueCard(revenue: RevenueReport) {
    SectionCard(title = "Revenue · last 30 days") {
        Metric("Net", "${revenue.currency} ${grouped(revenue.totalNet)}")
        Metric("Gross", "${revenue.currency} ${grouped(revenue.totalGross)}")
        Metric("Refunds", "${revenue.currency} ${grouped(revenue.totalRefunds)}")
        Metric("Tickets sold", grouped(revenue.totalTicketsSold))
    }
}

@Composable
private fun CapacityCard(capacity: CapacityReport) {
    SectionCard(title = "Capacity · last 30 days") {
        Metric("Avg occupancy", "${(capacity.avgOccupancyRate * 100).roundToInt()}%")
        Metric("Seats booked", "${grouped(capacity.totalSeatsBooked)} / ${grouped(capacity.totalSeatsTotal)}")
        Metric("Showtimes", grouped(capacity.totalShowtimes))
    }
}

@Composable
private fun CapacityRowItem(row: CapacityRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlickQColors.SurfaceNavy, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = row.movieTitle, color = FlickQColors.TicketPaper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = row.roomName, color = FlickQColors.SeatAvailable, fontSize = 12.sp)
        }
        Text(
            text = "${(row.occupancyRate * 100).roundToInt()}%  ·  ${row.seatsBooked}/${row.seatsTotal}",
            color = FlickQColors.GoldHighlight,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlickQColors.DeepNavy, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, color = FlickQColors.GoldHighlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = FlickQColors.SeatAvailable, fontSize = 14.sp)
        Text(text = value, color = FlickQColors.TicketPaper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Thousands-grouped integer (e.g. 1234567 -> "1,234,567"). */
private fun grouped(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
