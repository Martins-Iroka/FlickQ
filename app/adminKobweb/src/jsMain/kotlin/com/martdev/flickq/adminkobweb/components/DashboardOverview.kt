package com.martdev.flickq.adminkobweb.components

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
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
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.components.icons.fa.FaArrowTrendUp
import com.varabyte.kobweb.silk.components.icons.fa.FaChair
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaLocationDot
import com.varabyte.kobweb.silk.components.icons.fa.FaMoneyBill
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px

/**
 * Dashboard landing body: quick-action buttons, a four-up metrics row, and an upcoming-showtimes /
 * recent-activity split. The figures are placeholder content matching the Figma overview — the
 * dashboard is not yet wired to a ViewModel (real metrics arrive with the Reports phase).
 */
@Composable
fun DashboardOverview() {
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(32.px),
    ) {
        QuickActions()
        MetricsRow()
        ListsRow()
    }
}

@Composable
private fun QuickActions() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.px)) {
        ActionButton("Add Movie", primary = true)
        ActionButton("Add Showtime", primary = false)
        ActionButton("Add Room", primary = false)
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean) {
    Row(
        modifier = Modifier
            .borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 20.px)
            .cursor(Cursor.Pointer)
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
private fun MetricsRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        StatCard(
            label = "Total Revenue",
            value = "₦450,000",
            footer = "Net revenue for current month",
            trend = "+12%",
            icon = { FaMoneyBill(it) },
        )
        StatCard(
            label = "Tickets Sold",
            value = "1,240",
            footer = "Total tickets sold this month",
            trend = "+5%",
            icon = { FaTicket(it) },
        )
        StatCard(
            label = "Average Occupancy",
            value = "68%",
            footer = "Average seat fill rate",
            progress = 68,
            icon = { FaChair(it) },
        )
        StatCard(
            label = "Active Showtimes",
            value = "12",
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
    trend: String? = null,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            icon(Modifier.color(AdminColors.Body).fontSize(28.px))
            trend?.let {
                Row(
                    modifier = Modifier
                        .backgroundColor(AdminColors.SuccessWash)
                        .borderRadius(9999.px)
                        .padding(topBottom = 4.px, leftRight = 8.px),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.px),
                ) {
                    FaArrowTrendUp(Modifier.color(AdminColors.Success).fontSize(11.px))
                    SpanText(it, Modifier.color(AdminColors.Success).fontSize(12.px).fontWeight(FontWeight.Medium))
                }
            }
        }
        SpanText(
            label,
            Modifier.color(AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold).margin(top = 12.px),
        )
        SpanText(
            value,
            Modifier.montserrat().color(AdminColors.Heading).fontSize(32.px).fontWeight(FontWeight.Bold),
        )
        progress?.let { pct ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.px)
                    .backgroundColor(AdminColors.Chip)
                    .borderRadius(9999.px)
                    .margin(top = 4.px),
            ) {
                Box(
                    modifier = Modifier
                        .width(pct.percent)
                        .height(6.px)
                        .backgroundColor(AdminColors.Amber)
                        .borderRadius(9999.px),
                )
            }
        }
        SpanText(footer, Modifier.color(AdminColors.Body).fontSize(12.px).margin(top = 4.px))
    }
}

@Composable
private fun ListsRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        Box(modifier = Modifier.flexGrow(2).flexBasis(0.px)) { UpcomingShowtimesCard() }
        Box(modifier = Modifier.flexGrow(1).flexBasis(0.px)) { RecentActivityCard() }
    }
}

@Composable
private fun CardShell(title: String, action: String? = null, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.px)
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
                SpanText(it, Modifier.color(AdminColors.BodyStrong).fontSize(12.px).cursor(Cursor.Pointer))
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

private data class ShowtimeRow(
    val movie: String,
    val room: String,
    val time: String,
    val fill: String,
    val seats: String,
    val full: Boolean,
)

@Composable
private fun UpcomingShowtimesCard() {
    val rows = listOf(
        ShowtimeRow("Dune: Part Two", "IMAX Room 1", "14:30", "65% Full", "130/200 seats", full = false),
        ShowtimeRow("Oppenheimer", "Standard Room 3", "15:00", "92% Full", "110/120 seats", full = true),
    )
    CardShell("Upcoming Showtimes Today", action = "View All") {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().borderRadius(8.px).padding(12.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.px),
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.px)
                            .height(64.px)
                            .backgroundColor(AdminColors.Chip)
                            .borderRadius(4.px),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                        SpanText(
                            row.movie,
                            Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.px),
                        ) {
                            MetaItem({ FaLocationDot(it) }, row.room)
                            MetaItem({ FaClock(it) }, row.time)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.px)) {
                    val chipBg = if (row.full) AdminColors.SuccessChip else AdminColors.AmberWash
                    val chipFg = if (row.full) AdminColors.Success else AdminColors.Amber
                    Box(
                        modifier = Modifier
                            .backgroundColor(chipBg)
                            .borderRadius(9999.px)
                            .padding(topBottom = 4.px, leftRight = 10.px),
                    ) {
                        SpanText(row.fill, Modifier.color(chipFg).fontSize(12.px).fontWeight(FontWeight.Medium))
                    }
                    SpanText(row.seats, Modifier.color(AdminColors.Body).fontSize(12.px))
                }
            }
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

private data class ActivityRow(val title: String, val detail: String, val time: String, val dot: ActivityDot)
private enum class ActivityDot { Red, Green, Neutral }

@Composable
private fun RecentActivityCard() {
    val rows = listOf(
        ActivityRow("New Reservation", "Order #8829 for 4 VIP seats - Dune: Part Two.", "2 mins ago", ActivityDot.Red),
        ActivityRow("System Update", "Payment gateway sync completed successfully.", "15 mins ago", ActivityDot.Green),
        ActivityRow("Movie Added", "\"The Creator\" added to catalog by SuperAdmin.", "1 hour ago", ActivityDot.Neutral),
    )
    CardShell("Recent Activity") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.px),
            verticalArrangement = Arrangement.spacedBy(24.px),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.px)) {
                    val dotColor = when (row.dot) {
                        ActivityDot.Red -> AdminColors.Primary
                        ActivityDot.Green -> AdminColors.Success
                        ActivityDot.Neutral -> AdminColors.Heading
                    }
                    Box(
                        modifier = Modifier
                            .size(24.px)
                            .backgroundColor(AdminColors.Chip)
                            .border(2.px, LineStyle.Solid, AdminColors.Bg)
                            .borderRadius(9999.px),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.size(8.px).backgroundColor(dotColor).borderRadius(9999.px))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                        SpanText(
                            row.title,
                            Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold),
                        )
                        SpanText(row.detail, Modifier.color(AdminColors.Body).fontSize(14.px))
                        SpanText(row.time, Modifier.color(AdminColors.Body).fontSize(12.px))
                    }
                }
            }
        }
    }
}
