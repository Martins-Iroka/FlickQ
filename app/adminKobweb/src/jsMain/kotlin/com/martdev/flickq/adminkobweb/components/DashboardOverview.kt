package com.martdev.flickq.adminkobweb.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.feature.admin.presentation.logic.dashboard.AdminDashboardAction
import com.martdev.flickq.feature.admin.presentation.logic.dashboard.AdminDashboardState
import com.martdev.flickq.feature.admin.presentation.logic.dashboard.AdminDashboardViewModel
import com.martdev.flickq.feature.admin.presentation.logic.dashboard.UpcomingShowtimeItem
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
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaLocationDot
import com.varabyte.kobweb.silk.components.icons.fa.FaMoneyBill
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px

/**
 * Dashboard landing body: quick-action buttons, a four-up metrics row, and an upcoming-showtimes
 * card. Wired to [AdminDashboardViewModel], which composes live figures from the `admin/reports`
 * endpoints (net revenue + tickets for the trailing 30 days, average occupancy, and today's
 * showtimes). The Recent Activity card was removed pending a backend activity-log endpoint.
 */
@Composable
fun DashboardOverview() {
    val ctx = rememberPageContext()
    val vm = rememberAdminViewModel<AdminDashboardViewModel>()
    val state by vm.state.collectAsState()
    val error = state.error

    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(32.px),
    ) {
        QuickActions { route -> ctx.router.navigateTo(route) }
        when {
            state.isLoading -> StatusBox("Loading dashboard…")
            error != null -> ErrorBox(error) { vm.onAction(AdminDashboardAction.OnRetry) }
            else -> {
                MetricsRow(state)
                UpcomingShowtimesCard(state.upcomingToday) {
                    ctx.router.navigateTo("/showtimes") }
            }
        }
    }
}

@Composable
private fun QuickActions(onNavigate: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.px)) {
        ActionButton("Add Movie", primary = true) { onNavigate("/admin/movies/item?mode=add") }
        ActionButton("Add Showtime", primary = false) { onNavigate("/admin/showtime/info?mode=add") }
        ActionButton("Add Room", primary = false) { onNavigate("/admin/room/info?mode=add") }
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 20.px)
            .cursor(Cursor.Pointer)
            .onClick { onClick() }
            .then(
                if (primary) {
                    Modifier.backgroundColor(AdminColors.Primary)
                } else {
                    Modifier.backgroundColor(AdminColors.Chip).border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        val fg = if (primary) AdminColors.OnPrimary else AdminColors.Heading
        FaPlus(Modifier.color(fg).fontSize(14.px))
        SpanText(label, Modifier.color(fg).fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun MetricsRow(state: AdminDashboardState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        StatCard(
            label = "Total Revenue",
            value = formatNaira(state.totalNetRevenue),
            footer = "Net revenue · last 30 days",
            icon = { FaMoneyBill(it) },
        )
        StatCard(
            label = "Tickets Sold",
            value = groupDigits(state.ticketsSold),
            footer = "Tickets sold · last 30 days",
            icon = { FaTicket(it) },
        )
        StatCard(
            label = "Active Showtimes",
            value = state.activeShowtimesToday.toString(),
            footer = "Scheduled showtimes today",
            icon = { FaClock(it) },
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    footer: String,
    icon: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .styleModifier { property("flex", "1 1 0") }
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
        SpanText(
            value,
            Modifier.montserrat().color(AdminColors.Heading).fontSize(32.px).fontWeight(FontWeight.Bold),
        )
        SpanText(footer, Modifier.color(AdminColors.Body).fontSize(12.px).margin(top = 4.px))
    }
}

@Composable
private fun UpcomingShowtimesCard(rows: List<UpcomingShowtimeItem>, onViewAll: () -> Unit) {
    CardShell("Upcoming Showtimes Today", action = "View All", onAction = onViewAll) {
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.px)) {
                SpanText("No showtimes scheduled today.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
        } else {
            rows.forEach { row -> UpcomingShowtimeRow(row) }
        }
    }
}

@Composable
private fun UpcomingShowtimeRow(row: UpcomingShowtimeItem) {
    val full = row.occupancyPct >= 80
    Row(
        modifier = Modifier.fillMaxWidth().borderRadius(8.px).padding(12.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
            Box(
                modifier = Modifier
                    .width(48.px)
                    .height(64.px)
                    .backgroundColor(AdminColors.Chip)
                    .borderRadius(4.px),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText(
                    row.movieTitle,
                    Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                    MetaItem({ FaLocationDot(it) }, row.roomName)
                    MetaItem({ FaClock(it) }, dispTime(row.startsAt))
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.px)) {
            val chipBg = if (full) AdminColors.SuccessChip else AdminColors.AmberWash
            val chipFg = if (full) AdminColors.Success else AdminColors.Amber
            Box(
                modifier = Modifier
                    .backgroundColor(chipBg)
                    .borderRadius(9999.px)
                    .padding(topBottom = 4.px, leftRight = 10.px),
            ) {
                SpanText("${row.occupancyPct}% Full", Modifier.color(chipFg).fontSize(12.px).fontWeight(FontWeight.Medium))
            }
            SpanText(
                "${row.seatsBooked}/${row.seatsTotal} seats",
                Modifier.color(AdminColors.Body).fontSize(12.px),
            )
        }
    }
}

@Composable
private fun CardShell(title: String, action: String? = null, onAction: () -> Unit = {}, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px)
            .styleModifier { property("overflow", "hidden") },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText(
                title,
                Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.SemiBold),
            )
            action?.let {
                SpanText(
                    it,
                    Modifier.color(AdminColors.BodyStrong).fontSize(12.px).cursor(Cursor.Pointer).onClick { onAction() },
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.px),
            verticalArrangement = Arrangement.spacedBy(4.px),
        ) {
            body()
        }
    }
}

@Composable
private fun MetaItem(icon: @Composable (Modifier) -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.px)) {
        icon(Modifier.color(AdminColors.Body).fontSize(11.px))
        SpanText(text, Modifier.color(AdminColors.Body).fontSize(12.px))
    }
}
