package com.martdev.flickq.adminkobweb.components

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.feature.admin.presentation.logic.hub.AdminHubViewModel
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
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
import com.varabyte.kobweb.compose.ui.modifiers.borderRight
import com.varabyte.kobweb.compose.ui.modifiers.borderTop
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.components.icons.fa.FaBell
import com.varabyte.kobweb.silk.components.icons.fa.FaChartColumn
import com.varabyte.kobweb.silk.components.icons.fa.FaClapperboard
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaDoorOpen
import com.varabyte.kobweb.silk.components.icons.fa.FaFilm
import com.varabyte.kobweb.silk.components.icons.fa.FaGauge
import com.varabyte.kobweb.silk.components.icons.fa.FaRightFromBracket
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.icons.fa.FaUser
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh

/** Sidebar destinations, in display order. Routes for unbuilt phases 404 until their page lands. */
enum class AdminNav(val label: String, val route: String) {
    Dashboard("Dashboard", "/"),
    Movies("Movies", "/movies"),
    Rooms("Rooms", "/rooms"),
    Showtimes("Showtimes", "/showtimes"),
    Reservations("Reservations", "/reservations"),
    Reports("Reports", "/reports"),
}

@Composable
private fun NavIcon(item: AdminNav, modifier: Modifier) {
    when (item) {
        AdminNav.Dashboard -> FaGauge(modifier)
        AdminNav.Movies -> FaFilm(modifier)
        AdminNav.Rooms -> FaDoorOpen(modifier)
        AdminNav.Showtimes -> FaClock(modifier)
        AdminNav.Reservations -> FaTicket(modifier)
        AdminNav.Reports -> FaChartColumn(modifier)
    }
}

/**
 * The CineAdmin shell: a fixed 260px left sidebar (brand, nav, user + logout) and a scrollable
 * main column with a top bar. Pages provide their [selected] nav item, a [title] for the top bar,
 * and their body as [content].
 */
@Composable
fun AdminLayout(
    selected: AdminNav,
    title: String,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().minHeight(100.vh).backgroundColor(AdminColors.Bg)) {
        Sidebar(selected)
        Column(
            modifier = Modifier
                .flexGrow(1)
                .minHeight(100.vh)
                .backgroundColor(AdminColors.Bg)
                .overflow(Overflow.Auto),
        ) {
            TopBar(title)
            Box(modifier = Modifier.fillMaxWidth().padding(topBottom = 32.px, leftRight = 32.px)) {
                content()
            }
        }
    }
}

@Composable
private fun Sidebar(selected: AdminNav) {
    val ctx = rememberPageContext()
    val hubVm = rememberAdminViewModel<AdminHubViewModel>()

    Column(
        modifier = Modifier
            .width(260.px)
            .minHeight(100.vh)
            .backgroundColor(AdminColors.SurfaceAlt)
            .borderRight(1.px, LineStyle.Solid, AdminColors.BorderWarm),
    ) {
        // Brand area.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.px)
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(leftRight = 24.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            FaClapperboard(Modifier.color(AdminColors.Primary).fontSize(1.5.cssRem))
            SpanText(
                "Cinema Admin",
                Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold),
            )
        }

        // Navigation links.
        Column(
            modifier = Modifier
                .flexGrow(1)
                .fillMaxWidth()
                .padding(topBottom = 24.px, leftRight = 16.px),
            verticalArrangement = Arrangement.spacedBy(8.px),
        ) {
            AdminNav.entries.forEach { item ->
                val active = item == selected
                val tint = if (active) AdminColors.Primary else AdminColors.Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .borderRadius(8.px)
                        .padding(topBottom = 12.px, leftRight = 16.px)
                        .cursor(Cursor.Pointer)
                        .thenIf(active) { Modifier.backgroundColor(AdminColors.PrimaryWash) }
                        .onClick { ctx.router.navigateTo(item.route) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.px),
                ) {
                    NavIcon(item, Modifier.color(tint).fontSize(1.cssRem).width(20.px))
                    SpanText(item.label, Modifier.color(tint).fontSize(14.px).fontWeight(FontWeight.SemiBold))
                }
            }
        }

        // User area.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .borderTop(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(topBottom = 16.px, leftRight = 16.px),
            verticalArrangement = Arrangement.spacedBy(16.px),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(leftRight = 8.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.px),
            ) {
                Box(
                    modifier = Modifier.size(32.px).borderRadius(9999.px).backgroundColor(AdminColors.Chip),
                    contentAlignment = Alignment.Center,
                ) {
                    FaUser(Modifier.color(AdminColors.Body).fontSize(0.8.cssRem))
                }
                SpanText("Administrator", Modifier.color(AdminColors.Body).fontSize(12.px))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                    .borderRadius(8.px)
                    .padding(topBottom = 9.px)
                    .cursor(Cursor.Pointer)
                    .onClick { hubVm.logout() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FaRightFromBracket(Modifier.color(AdminColors.Heading).fontSize(0.9.cssRem).margin(right = 8.px))
                SpanText("Logout", Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun TopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.px)
            .backgroundColor(AdminColors.Bg)
            .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .padding(leftRight = 32.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SpanText(
            title,
            Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.SemiBold),
        )
        Box(
            modifier = Modifier.padding(8.px).styleModifier { property("position", "relative") },
            contentAlignment = Alignment.Center,
        ) {
            FaBell(Modifier.color(AdminColors.Body).fontSize(1.1.cssRem))
            Box(
                modifier = Modifier
                    .styleModifier {
                        property("position", "absolute")
                        property("top", "2px")
                        property("right", "2px")
                    }
                    .size(10.px)
                    .backgroundColor(AdminColors.Primary)
                    .border(2.px, LineStyle.Solid, AdminColors.Bg)
                    .borderRadius(9999.px),
            )
        }
    }
}
