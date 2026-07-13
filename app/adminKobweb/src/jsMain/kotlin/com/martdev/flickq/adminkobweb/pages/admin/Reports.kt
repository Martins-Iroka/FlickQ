package com.martdev.flickq.adminkobweb.pages.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.admin.presentation.logic.reports.AdminReportsAction
import com.martdev.flickq.feature.admin.presentation.logic.reports.AdminReportsViewModel
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.RevenueReport
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderBottom
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.icons.fa.FaArrowTrendUp
import com.varabyte.kobweb.silk.components.icons.fa.FaChair
import com.varabyte.kobweb.silk.components.icons.fa.FaChartColumn
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaMoneyBill
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import kotlin.math.roundToInt

@Page
@Composable
fun ReportsPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Reports, title = "Reports") {
            ReportsContent()
        }
    }
}

@Composable
private fun ReportsContent() {
    val vm = rememberAdminViewModel<AdminReportsViewModel>()
    val state by vm.state.collectAsState()
    val error = state.error

    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(32.px),
    ) {
        when {
            state.isLoading -> StatusBox("Loading reports…")
            error != null -> ErrorBox(error) { vm.onAction(AdminReportsAction.OnRetry) }
            else -> {
                state.revenue?.let { RevenueSection(it) }
                state.capacity?.let { CapacitySection(it) }
            }
        }
    }
}

@Composable
private fun RevenueSection(revenue: RevenueReport) {
    val cur = revenue.currency
    SectionTitle("Revenue · Last 30 Days")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        StatTile("Net Revenue", "$cur ${grouped(revenue.totalNet)}", "Net of refunds") { FaMoneyBill(it) }
        StatTile("Gross Revenue", "$cur ${grouped(revenue.totalGross)}", "Before refunds") { FaChartColumn(it) }
        StatTile("Refunds", "$cur ${grouped(revenue.totalRefunds)}", "Returned to customers") { FaArrowTrendUp(it) }
        StatTile("Tickets Sold", grouped(revenue.totalTicketsSold), "Across all showtimes") { FaTicket(it) }
    }
}

@Composable
private fun CapacitySection(capacity: CapacityReport) {
    SectionTitle("Capacity · Last 30 Days")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        StatTile(
            "Average Occupancy",
            "${pct(capacity.avgOccupancyRate)}%",
            "Average seat fill rate",
            progress = pct(capacity.avgOccupancyRate),
        ) { FaChair(it) }
        StatTile(
            "Seats Booked",
            "${grouped(capacity.totalSeatsBooked)} / ${grouped(capacity.totalSeatsTotal)}",
            "Booked vs. total capacity",
        ) { FaTicket(it) }
        StatTile("Showtimes", grouped(capacity.totalShowtimes), "Scheduled in window") { FaClock(it) }
    }
    OccupancyTable(capacity.rows)
}

@Composable
private fun OccupancyTable(rows: List<CapacityRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpanText(
                "Showtime Occupancy",
                Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.SemiBold),
            )
        }
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.px)) {
                SpanText("No showtimes in this window.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(8.px)) {
                rows.forEach { OccupancyRow(it) }
            }
        }
    }
}

@Composable
private fun OccupancyRow(row: CapacityRow) {
    val rate = pct(row.occupancyRate)
    val badgeFg = when {
        rate >= 80 -> AdminColors.Success
        rate >= 50 -> AdminColors.Amber
        else -> AdminColors.Body
    }
    val badgeBg = when {
        rate >= 80 -> AdminColors.SuccessChip
        rate >= 50 -> AdminColors.AmberWash
        else -> AdminColors.Chip
    }
    Row(
        modifier = Modifier.fillMaxWidth().borderRadius(8.px).padding(12.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.px)) {
            SpanText(
                row.movieTitle,
                Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold),
            )
            SpanText(row.roomName, Modifier.color(AdminColors.Body).fontSize(12.px))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(
                modifier = Modifier
                    .backgroundColor(badgeBg)
                    .borderRadius(9999.px)
                    .padding(topBottom = 4.px, leftRight = 10.px),
            ) {
                SpanText("$rate% Full", Modifier.color(badgeFg).fontSize(12.px).fontWeight(FontWeight.Medium))
            }
            SpanText(
                "${grouped(row.seatsBooked.toLong())}/${grouped(row.seatsTotal.toLong())} seats",
                Modifier.color(AdminColors.Body).fontSize(12.px),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    footer: String,
    progress: Int? = null,
    icon: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .flexGrow(1)
            .flexBasis(0.px)
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px)
            .padding(25.px),
        verticalArrangement = Arrangement.spacedBy(4.px),
    ) {
        icon(Modifier.color(AdminColors.Body).fontSize(28.px))
        SpanText(
            label,
            Modifier.color(AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold).margin(top = 12.px),
        )
        SpanText(value, Modifier.montserrat().color(AdminColors.Heading).fontSize(28.px).fontWeight(FontWeight.Bold))
        progress?.let { p ->
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(6.px).backgroundColor(AdminColors.Chip)
                    .borderRadius(9999.px).margin(top = 4.px),
            ) {
                Box(Modifier.width(p.coerceIn(0, 100).percent).height(6.px).backgroundColor(AdminColors.Amber).borderRadius(9999.px))
            }
        }
        SpanText(footer, Modifier.color(AdminColors.Body).fontSize(12.px).margin(top = 4.px))
    }
}

@Composable
private fun SectionTitle(text: String) {
    SpanText(text, Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.SemiBold))
}

@Composable
private fun StatusBox(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.px), contentAlignment = Alignment.Center) {
        SpanText(message, Modifier.color(AdminColors.Muted).fontSize(16.px))
    }
}

@Composable
private fun ErrorBox(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px)
            .padding(32.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.px),
    ) {
        SpanText(error.plain(), Modifier.color(AdminColors.Body).fontSize(14.px))
        Box(
            modifier = Modifier
                .backgroundColor(AdminColors.Primary)
                .color(AdminColors.OnPrimary)
                .borderRadius(8.px)
                .padding(topBottom = 11.px, leftRight = 20.px)
                .cursor(Cursor.Pointer)
                .onClick { onRetry() },
        ) {
            SpanText("Retry", Modifier.fontWeight(FontWeight.SemiBold))
        }
    }
}

private fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

private fun pct(rate: Double): Int = (rate * 100).roundToInt()

/** Thousands-grouped integer (e.g. 1234567 -> "1,234,567"). */
private fun grouped(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
