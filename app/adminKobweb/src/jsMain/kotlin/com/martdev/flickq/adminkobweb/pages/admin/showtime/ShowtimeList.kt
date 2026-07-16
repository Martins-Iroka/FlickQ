package com.martdev.flickq.adminkobweb.pages.admin.showtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.DotBadge
import com.martdev.flickq.adminkobweb.components.Dropdown
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.FIELD_CSS
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.Overlay
import com.martdev.flickq.adminkobweb.components.PagerButton
import com.martdev.flickq.adminkobweb.components.PosterThumb
import com.martdev.flickq.adminkobweb.components.PrimaryButton
import com.martdev.flickq.adminkobweb.components.PrimaryButtonPlain
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.dispDate
import com.martdev.flickq.adminkobweb.components.dispTime
import com.martdev.flickq.adminkobweb.components.formatNaira
import com.martdev.flickq.adminkobweb.components.isoDay
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.components.titlecaseWord
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesAction
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesState
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesViewModel
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.ShowtimeListEvent
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import com.varabyte.kobweb.browser.storage.setItem
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextDecorationLine
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
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textDecorationLine
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Input

@Page("list")
@Composable
fun ShowtimeListScreen() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Showtimes, title = "Showtime") {
            ShowtimeListContent()
        }
    }
}

@Composable
private fun ShowtimeListContent() {
    val ctx = rememberPageContext()
    val vm = rememberAdminViewModel<AdminShowtimesViewModel>()
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    ObserveAsEvents(vm.event) { event ->
        when(event) {
            ShowtimeListEvent.NavigateToAddNewShowtime -> ctx.router.navigateTo("/admin/showtime/info?mode=add")
            is ShowtimeListEvent.NavigateToEditShowtime -> {
                val showtimeStorageKey = ShowtimeStorageKey("showtime")
                sessionStorage.setItem(showtimeStorageKey, event.data)
                ctx.router.navigateTo("/admin/showtime/info?id=${event.data.editingId}&mode=edit")
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        ShowtimeListView(state, onAction)

        state.deleting?.let { st -> DeleteConfirm(st, onAction) }
        state.populatingFor?.let { st -> PopulateConfirm(st, onAction) }
        state.statusFor?.let { st -> StatusPicker(st, onAction) }
    }
}

// ---- List ---------------------------------------------------------------------------------

@Composable
private fun ShowtimeListView(state: AdminShowtimesState, onAction: (AdminShowtimesAction) -> Unit) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    val pageSize = 10

    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText("Showtime Management", Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold))
                SpanText("Manage schedules, room assignments, and pricing.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
            PrimaryButton("Create Showtime") { onAction(AdminShowtimesAction.OnAddClick) }
        }

        state.message?.let { msg ->
            Box(
                modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SuccessWash)
                    .border(1.px, LineStyle.Solid, AdminColors.Success).borderRadius(8.px)
                    .padding(topBottom = 10.px, leftRight = 14.px),
            ) { SpanText(msg.plain(), Modifier.color(AdminColors.Success).fontSize(13.px)) }
        }

        val error = state.error
        when {
            state.isLoading -> StatusBox("Loading showtimes…")
            error != null -> ErrorBox(error) { onAction(AdminShowtimesAction.OnRetry) }
            else -> {
                // Client-side filter over the loaded pages.
                val filtered = state.showtimes.filter { st ->
                    val title = state.movie(st.movieId)?.title ?: ""
                    val room = state.room(st.roomId)?.name ?: ""
                    val q = query.trim().lowercase()
                    (q.isBlank() || q in title.lowercase() || q in room.lowercase()) &&
                        (statusFilter.isBlank() || st.status.name == statusFilter) &&
                        (dateFilter.isBlank() || isoDay(st.startsAt) == dateFilter)
                }
                ListCard(state, filtered, page, pageSize, query, statusFilter, dateFilter,
                    onQuery = { query = it; page = 0 },
                    onStatus = { statusFilter = it; page = 0 },
                    onDate = { dateFilter = it; page = 0 },
                    onPage = { page = it },
                    onAction = onAction)
            }
        }
    }
}

@Composable
private fun ListCard(
    state: AdminShowtimesState,
    filtered: List<Showtime>,
    page: Int,
    pageSize: Int,
    query: String,
    statusFilter: String,
    dateFilter: String,
    onQuery: (String) -> Unit,
    onStatus: (String) -> Unit,
    onDate: (String) -> Unit,
    onPage: (Int) -> Unit,
    onAction: (AdminShowtimesAction) -> Unit,
) {
    val total = filtered.size
    val lastPage = if (total == 0) 0 else (total - 1) / pageSize
    val safePage = page.coerceIn(0, lastPage)
    val start = safePage * pageSize
    val visible = filtered.drop(start).take(pageSize)

    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px),
    ) {
        // Filters.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(16.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            Box(modifier = Modifier.flexGrow(1)) {
                Input(type = InputType.Text) {
                    value(query)
                    attr("placeholder", "Search by movie or room…")
                    attr("style", FIELD_CSS)
                    onInput { onQuery(it.value) }
                }
            }
            Box(modifier = Modifier.width(180.px)) {
                Dropdown(
                    value = statusFilter,
                    placeholder = "All Statuses",
                    options = listOf("" to "All Statuses") + ShowtimeStatus.entries.map { it.name to it.name.titlecaseWord() },
                    onSelect = onStatus,
                )
            }
            Box(modifier = Modifier.width(170.px)) {
                Input(type = InputType.Date) {
                    value(dateFilter)
                    attr("style", FIELD_CSS)
                    onInput { onDate(it.value) }
                }
            }
        }
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
                .padding(topBottom = 12.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Movie", null)
            HeaderCell("Room", 150.px)
            HeaderCell("Start", 150.px)
            HeaderCell("End", 90.px)
            HeaderCell("Price", 110.px)
            HeaderCell("Status", 130.px)
            HeaderCell("Actions", 140.px)
        }
        when {
            total == 0 -> StatusBox("No showtimes match your filters.")
            else -> visible.forEach { st -> ShowtimeRow(state, st, onAction) }
        }
        // Footer / pagination.
        Row(
            modifier = Modifier.fillMaxWidth().padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val from = if (total == 0) 0 else start + 1
            val to = start + visible.size
            SpanText("Showing $from to $to of $total", Modifier.color(AdminColors.Body).fontSize(13.px))
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                if (state.canLoadMore) {
                    PagerButton("Load more", enabled = true) { onAction(AdminShowtimesAction.OnLoadMore) }
                }
                PagerButton("Prev", enabled = safePage > 0) { onPage(safePage - 1) }
                SpanText("${safePage + 1} / ${lastPage + 1}", Modifier.color(AdminColors.Body).fontSize(13.px))
                PagerButton("Next", enabled = safePage < lastPage) { onPage(safePage + 1) }
            }
        }
    }
}

@Composable
private fun ShowtimeRow(state: AdminShowtimesState, st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    val cancelled = st.status == ShowtimeStatus.CANCELLED
    val movie = state.movie(st.movieId)
    val roomName = state.room(st.roomId)?.name ?: "Room ${st.roomId}"
    Row(
        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px)
            .thenIf(cancelled) { Modifier.opacity(0.55) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                PosterThumb(movie?.posterUrl ?: "", 36, 50)
                SpanText(movie?.title ?: "Movie ${st.movieId}", Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
            }
        }
        Cell(150.px) {
            SpanText(
                roomName,
                Modifier.color(AdminColors.Body).fontSize(14.px)
                    .thenIf(cancelled) { Modifier.textDecorationLine(TextDecorationLine.LineThrough) },
            )
        }
        Cell(150.px) {
            Column(verticalArrangement = Arrangement.spacedBy(2.px)) {
                SpanText(dispDate(st.startsAt), Modifier.color(AdminColors.Body).fontSize(13.px))
                SpanText(dispTime(st.startsAt), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
            }
        }
        Cell(90.px) { SpanText(dispTime(st.endsAt), Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(110.px) { SpanText(formatNaira(st.price), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
        Cell(130.px) { StatusBadge(st.status) }
        Cell(140.px) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ FaPenToSquare(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnEditClick(st)) }
                IconButton({ FaClock(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnStatusClick(st)) }
                IconButton({ FaTicket(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnPopulateClick(st)) }
                IconButton({ FaTrash(it) }, AdminColors.Primary) { onAction(AdminShowtimesAction.OnDeleteClick(st)) }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ShowtimeStatus) {
    val (bg, fg) = when (status) {
        ShowtimeStatus.SCHEDULED -> AdminColors.SuccessChip to AdminColors.Success
        ShowtimeStatus.COMPLETED -> AdminColors.Chip to AdminColors.Muted
        ShowtimeStatus.CANCELLED -> AdminColors.PrimaryWash to AdminColors.Primary
    }
    DotBadge(status.name, bg, fg)
}

// ---- Overlays -----------------------------------------------------------------------------

@Composable
private fun DeleteConfirm(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Delete showtime", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText("Showtime ${st.id} will be removed. Showtimes with reservations can't be deleted.", Modifier.color(AdminColors.Body).fontSize(14.px))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminShowtimesAction.OnDismissDelete) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButton("Delete") { onAction(AdminShowtimesAction.OnConfirmDelete) } }
        }
    }
}

@Composable
private fun PopulateConfirm(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Populate seats", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText("Seed the showtime-seat grid for showtime ${st.id} from its room layout.", Modifier.color(AdminColors.Body).fontSize(14.px))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminShowtimesAction.OnDismissPopulate) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButtonPlain("Populate") { onAction(AdminShowtimesAction.OnConfirmPopulate) } }
        }
    }
}

@Composable
private fun StatusPicker(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Set status", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.px)) {
            ShowtimeStatus.entries.forEach { status ->
                val selected = status == st.status
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .backgroundColor(if (selected) AdminColors.PrimaryWash else AdminColors.Surface)
                        .border(1.px, LineStyle.Solid, if (selected) AdminColors.Primary else AdminColors.Border)
                        .borderRadius(8.px).padding(topBottom = 12.px, leftRight = 14.px)
                        .cursor(Cursor.Pointer).onClick { onAction(AdminShowtimesAction.OnStatusPicked(status)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpanText(status.name, Modifier.color(if (selected) AdminColors.Primary else AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold))
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) { SecondaryButton("Close") { onAction(AdminShowtimesAction.OnDismissStatus) } }
    }
}
