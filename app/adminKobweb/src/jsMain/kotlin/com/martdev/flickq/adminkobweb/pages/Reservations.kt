package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.DotBadge
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.FIELD_CSS
import com.martdev.flickq.adminkobweb.components.FieldLabel
import com.martdev.flickq.adminkobweb.components.FormCard
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.Overlay
import com.martdev.flickq.adminkobweb.components.PagerButton
import com.martdev.flickq.adminkobweb.components.PrimaryButtonPlain
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.dispDate
import com.martdev.flickq.adminkobweb.components.dispTime
import com.martdev.flickq.adminkobweb.components.formatNaira
import com.martdev.flickq.adminkobweb.components.groupDigits
import com.martdev.flickq.adminkobweb.components.isoDay
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.components.titlecaseWord
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailAction
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailState
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailViewModel
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsAction
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsState
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsViewModel
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
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
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.letterSpacing
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.textDecorationLine
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.icons.fa.FaArrowLeft
import com.varabyte.kobweb.silk.components.icons.fa.FaCircleInfo
import com.varabyte.kobweb.silk.components.icons.fa.FaCouch
import com.varabyte.kobweb.silk.components.icons.fa.FaDownload
import com.varabyte.kobweb.silk.components.icons.fa.FaEye
import com.varabyte.kobweb.silk.components.icons.fa.FaLock
import com.varabyte.kobweb.silk.components.icons.fa.FaMoneyBill
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Input
import org.koin.core.parameter.parametersOf
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Page
@Composable
fun ReservationsPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Reservations, title = "Reservations") {
            ReservationsContent()
        }
    }
}

@Composable
private fun ReservationsContent() {
    // The list VM is held at page level so loaded pages survive list ⇄ detail navigation.
    val listVm = rememberAdminViewModel<AdminReservationsViewModel>()
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val id = selectedId
    if (id == null) {
        ReservationListView(listVm) { selectedId = it }
    } else {
        key(id) {
            ReservationDetailView(id) {
                // Refresh on the way back so a cancel done in the detail view shows in the list.
                listVm.onAction(AdminReservationsAction.OnRetry)
                selectedId = null
            }
        }
    }
}

// ---- List ---------------------------------------------------------------------------------

@Composable
private fun ReservationListView(vm: AdminReservationsViewModel, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    var tab by remember { mutableStateOf<ReservationStatus?>(null) }
    var query by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    val pageSize = 10

    // Client-side filtering over the loaded pages. Server-side status/search/date filters and a
    // CSV export endpoint are a planned backend phase; until then counts reflect loaded rows only.
    val filtered = state.reservations.filter { r ->
        val movieTitle = state.showtime(r.showtimeId)?.let { state.movie(it.movieId)?.title } ?: ""
        val q = query.trim().lowercase()
        val created = isoDay(r.createdAt)
        (tab == null || r.status == tab) &&
            (q.isBlank() || q in "rsv-${r.id}" || q in "usr-${r.userId}" || q in movieTitle.lowercase()) &&
            (dateFrom.isBlank() || created >= dateFrom) &&
            (dateTo.isBlank() || created <= dateTo)
    }

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
                SpanText("Reservation Management", Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold))
                SpanText("Manage and track all customer bookings.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
            ExportButton { exportCsv(state, filtered) }
        }

        val error = state.error
        when {
            state.isLoading -> StatusBox("Loading reservations…")
            error != null -> ErrorBox(error) { onAction(AdminReservationsAction.OnRetry) }
            else -> ListCard(
                state, filtered, tab, query, dateFrom, dateTo, page, pageSize,
                onTab = { tab = it; page = 0 },
                onQuery = { query = it; page = 0 },
                onDateFrom = { dateFrom = it; page = 0 },
                onDateTo = { dateTo = it; page = 0 },
                onPage = { page = it },
                onLoadMore = { onAction(AdminReservationsAction.OnLoadMore) },
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun ListCard(
    state: AdminReservationsState,
    filtered: List<Reservation>,
    tab: ReservationStatus?,
    query: String,
    dateFrom: String,
    dateTo: String,
    page: Int,
    pageSize: Int,
    onTab: (ReservationStatus?) -> Unit,
    onQuery: (String) -> Unit,
    onDateFrom: (String) -> Unit,
    onDateTo: (String) -> Unit,
    onPage: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Long) -> Unit,
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
        // Status tabs (counts are over loaded rows until a server-side filter exists).
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(leftRight = 8.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab("ALL (${state.reservations.size})", tab == null) { onTab(null) }
            ReservationStatus.entries.forEach { status ->
                val count = state.reservations.count { it.status == status }
                Tab("${status.name} ($count)", tab == status) { onTab(status) }
            }
        }
        // Search + date-range filters.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(16.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            Box(modifier = Modifier.flexGrow(1)) {
                Input(type = InputType.Text) {
                    value(query)
                    attr("placeholder", "Search by id, user, or movie…")
                    attr("style", FIELD_CSS)
                    onInput { onQuery(it.value) }
                }
            }
            SpanText("Date range", Modifier.color(AdminColors.Muted).fontSize(13.px))
            Box(modifier = Modifier.width(160.px)) {
                Input(type = InputType.Date) {
                    value(dateFrom)
                    attr("style", FIELD_CSS)
                    onInput { onDateFrom(it.value) }
                }
            }
            SpanText("–", Modifier.color(AdminColors.Muted).fontSize(13.px))
            Box(modifier = Modifier.width(160.px)) {
                Input(type = InputType.Date) {
                    value(dateTo)
                    attr("style", FIELD_CSS)
                    onInput { onDateTo(it.value) }
                }
            }
        }
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
                .padding(topBottom = 12.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("ID", 110.px)
            HeaderCell("USER ID", 110.px)
            HeaderCell("MOVIE / SHOWTIME", null)
            HeaderCell("SEATS", 60.px)
            HeaderCell("TOTAL", 100.px)
            HeaderCell("STATUS", 120.px)
            HeaderCell("CREATED DATE", 120.px)
            HeaderCell("ACTIONS", 70.px)
        }
        when {
            total == 0 -> StatusBox("No reservations match your filters.")
            else -> visible.forEach { r -> ReservationRow(state, r, onOpen) }
        }
        // Footer / pagination.
        Row(
            modifier = Modifier.fillMaxWidth().padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val from = if (total == 0) 0 else start + 1
            val to = start + visible.size
            SpanText("Showing $from to $to of $total results", Modifier.color(AdminColors.Body).fontSize(13.px))
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                if (state.canLoadMore) {
                    PagerButton("Load more", enabled = true, onClick = onLoadMore)
                }
                PagerButton("Prev", enabled = safePage > 0) { onPage(safePage - 1) }
                SpanText("${safePage + 1} / ${lastPage + 1}", Modifier.color(AdminColors.Body).fontSize(13.px))
                PagerButton("Next", enabled = safePage < lastPage) { onPage(safePage + 1) }
            }
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(topBottom = 14.px, leftRight = 16.px).cursor(Cursor.Pointer)
            .thenIf(selected) { Modifier.styleModifier { property("box-shadow", "inset 0 -2px 0 0 #e50914") } }
            .onClick { onClick() },
    ) {
        SpanText(
            label,
            Modifier.fontSize(13.px).fontWeight(FontWeight.SemiBold)
                .color(if (selected) AdminColors.Heading else AdminColors.Muted),
        )
    }
}

@Composable
private fun ReservationRow(state: AdminReservationsState, r: Reservation, onOpen: (Long) -> Unit) {
    val cancelled = r.status == ReservationStatus.CANCELLED
    val showtime = state.showtime(r.showtimeId)
    val movieTitle = showtime?.let { state.movie(it.movieId)?.title } ?: "Showtime #${r.showtimeId}"
    val roomName = showtime?.let { state.room(it.roomId)?.name } ?: ""
    Row(
        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px)
            .cursor(Cursor.Pointer).onClick { onOpen(r.id) }
            .thenIf(cancelled) { Modifier.opacity(0.55) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(110.px) {
            SpanText(
                "#RSV-${r.id}",
                Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)
                    .thenIf(cancelled) { Modifier.textDecorationLine(TextDecorationLine.LineThrough) },
            )
        }
        Cell(110.px) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                Avatar("U${r.userId.toString().takeLast(1)}")
                SpanText("USR-${r.userId}", Modifier.color(AdminColors.Body).fontSize(13.px))
            }
        }
        Cell(null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.px)) {
                SpanText(movieTitle, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
                if (showtime != null) {
                    SpanText(
                        "${dispDate(showtime.startsAt)}, ${dispTime(showtime.startsAt)}${if (roomName.isNotBlank()) " - $roomName" else ""}",
                        Modifier.color(AdminColors.Muted).fontSize(12.px),
                    )
                }
            }
        }
        Cell(60.px) { SpanText("${r.seats.size}", Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(100.px) { SpanText(formatNaira(r.totalAmount), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
        Cell(120.px) { ReservationBadge(r.status) }
        Cell(120.px) { SpanText("${dispDate(r.createdAt)}, ${dispTime(r.createdAt)}", Modifier.color(AdminColors.Body).fontSize(13.px)) }
        Cell(70.px) {
            IconButton({ FaEye(it) }, AdminColors.Body) { onOpen(r.id) }
        }
    }
}

// ---- Detail -------------------------------------------------------------------------------

@Composable
private fun ReservationDetailView(reservationId: Long, onBack: () -> Unit) {
    val vm = rememberAdminViewModel<AdminReservationDetailViewModel> { parametersOf(reservationId) }
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BackButton(onBack)
            if (state.canCancel) {
                CancelButton(if (state.isCancelling) "Cancelling…" else "Cancel Reservation", enabled = !state.isCancelling) {
                    onAction(AdminReservationDetailAction.OnCancelClick)
                }
            }
        }

        val error = state.error
        when {
            state.isLoading -> StatusBox("Loading reservation…")
            error != null -> ErrorBox(error) { onAction(AdminReservationDetailAction.OnRetry) }
            else -> state.reservation?.let { r -> DetailBody(state, r, onAction) }
        }
    }

    if (state.showCancelConfirm) { CancelConfirm(onAction) }
}

@Composable
private fun DetailBody(state: AdminReservationDetailState, r: Reservation, onAction: (AdminReservationDetailAction) -> Unit) {
    // Header.
    Column(
        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 20.px),
        verticalArrangement = Arrangement.spacedBy(8.px),
    ) {
        SpanText("RESERVATION DETAILS", Modifier.color(AdminColors.Muted).fontSize(12.px).fontWeight(FontWeight.SemiBold).letterSpacing(1.px))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
                SpanText("RES-${r.id}", Modifier.montserrat().color(AdminColors.Heading).fontSize(32.px).fontWeight(FontWeight.Bold))
                ReservationBadge(r.status)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.px)) {
                SpanText("Created At", Modifier.color(AdminColors.Muted).fontSize(12.px))
                SpanText("${dispDate(r.createdAt)} • ${dispTime(r.createdAt)}", Modifier.color(AdminColors.Heading).fontSize(15.px).fontWeight(FontWeight.SemiBold))
            }
        }
    }

    state.message?.let { msg ->
        Box(
            modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SuccessWash)
                .border(1.px, LineStyle.Solid, AdminColors.Success).borderRadius(8.px)
                .padding(topBottom = 10.px, leftRight = 14.px),
        ) { SpanText(msg.plain(), Modifier.color(AdminColors.Success).fontSize(13.px)) }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
        // Left: general info (no user-lookup API exists — only the numeric user id is shown).
        Column(modifier = Modifier.flexGrow(1).flexBasis(0.px).minWidth(280.px)) {
            FormCard("General Info", { FaCircleInfo(it) }) {
                FieldLabel("USER / CUSTOMER")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                    Avatar("U${r.userId.toString().takeLast(1)}")
                    SpanText("User #${r.userId} (USR-${r.userId})", Modifier.color(AdminColors.Heading).fontSize(15.px).fontWeight(FontWeight.SemiBold))
                }
                Box(Modifier.fillMaxWidth().height(1.px).backgroundColor(AdminColors.Border).margin(topBottom = 4.px))
                FieldLabel("MOVIE")
                SpanText(state.movie?.title ?: "Showtime #${r.showtimeId}", Modifier.color(AdminColors.Heading).fontSize(16.px).fontWeight(FontWeight.SemiBold))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
                    Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                        FieldLabel("ROOM")
                        SpanText(state.room?.name ?: "—", Modifier.color(AdminColors.Heading).fontSize(15.px))
                    }
                    Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(4.px)) {
                        FieldLabel("DATE & TIME")
                        val st = state.showtime
                        SpanText(
                            if (st != null) "${dispDate(st.startsAt)} • ${dispTime(st.startsAt)}" else "—",
                            Modifier.color(AdminColors.Heading).fontSize(15.px),
                        )
                    }
                }
            }
        }
        // Right: reserved seats.
        Column(modifier = Modifier.flexGrow(2).flexBasis(0.px).minWidth(380.px)) {
            FormCard("Reserved Seats", { FaCouch(it) }, badge = "${r.seats.size} Seats Total") {
                Row(
                    modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 10.px),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderCell("SEAT ID", null)
                    HeaderCell("ROW", 100.px)
                    HeaderCell("NUMBER", 100.px)
                    HeaderCell("STATUS", 90.px)
                }
                r.seats.forEach { seat ->
                    val layout = state.seat(seat.seatId)
                    Row(
                        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
                            .padding(topBottom = 12.px),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cell(null) { SpanText("ST-${seat.seatId}", Modifier.color(AdminColors.Body).fontSize(14.px)) }
                        Cell(100.px) { SpanText(layout?.rowLabel ?: "—", Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
                        Cell(100.px) { SpanText(layout?.seatNumber?.toString() ?: "—", Modifier.color(AdminColors.Heading).fontSize(14.px)) }
                        Cell(90.px) { SeatBadge(seat.status) }
                    }
                }
                if (r.seats.isEmpty()) {
                    SpanText("No seats attached to this reservation.", Modifier.color(AdminColors.Muted).fontSize(13.px))
                }
            }
        }
    }

    // Payment history.
    FormCard("Payment History", { FaMoneyBill(it) }) {
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 10.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("REFERENCE", null)
            HeaderCell("AMOUNT (₦)", 120.px)
            HeaderCell("STATUS", 130.px)
            HeaderCell("GATEWAY RESPONSE", 200.px)
            HeaderCell("PAID AT", 130.px)
            HeaderCell("CREATED AT", 130.px)
        }
        state.payments.forEach { p -> PaymentRow(p) }
        if (state.payments.isEmpty()) {
            SpanText("No payment attempts recorded.", Modifier.color(AdminColors.Muted).fontSize(13.px))
        }
    }
}

@Composable
private fun PaymentRow(p: Payment) {
    Row(
        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(topBottom = 12.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(null) { SpanText(p.reference.ifBlank { "—" }, Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(120.px) { SpanText(groupDigits(p.amount), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
        Cell(130.px) { PaymentBadge(p.status) }
        Cell(200.px) { SpanText(p.gatewayResponse ?: "—", Modifier.color(AdminColors.Body).fontSize(13.px)) }
        Cell(130.px) { SpanText(p.paidAt?.let { "${dispDate(it)}, ${dispTime(it)}" } ?: "--", Modifier.color(AdminColors.Body).fontSize(13.px)) }
        Cell(130.px) { SpanText("${dispDate(p.createdAt)}, ${dispTime(p.createdAt)}", Modifier.color(AdminColors.Body).fontSize(13.px)) }
    }
}

@Composable
private fun CancelConfirm(onAction: (AdminReservationDetailAction) -> Unit) {
    Overlay {
        SpanText("Cancel reservation?", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText("This releases the held seats and marks the reservation cancelled. This can't be undone.", Modifier.color(AdminColors.Body).fontSize(14.px))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Keep reservation") { onAction(AdminReservationDetailAction.OnDismissCancel) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButtonPlain("Cancel reservation", Modifier.fillMaxWidth()) { onAction(AdminReservationDetailAction.OnConfirmCancel) } }
        }
    }
}

// ---- Badges -------------------------------------------------------------------------------

@Composable
private fun ReservationBadge(status: ReservationStatus) {
    val (bg, fg) = when (status) {
        ReservationStatus.PENDING -> AdminColors.AmberWash to AdminColors.Amber
        ReservationStatus.CONFIRMED -> AdminColors.SuccessChip to AdminColors.Success
        ReservationStatus.CANCELLED -> AdminColors.PrimaryWash to AdminColors.Primary
    }
    DotBadge(status.name, bg, fg)
}

@Composable
private fun PaymentBadge(status: PaymentStatus) {
    val (bg, fg) = when (status) {
        PaymentStatus.SUCCESS, PaymentStatus.REFUNDED -> AdminColors.SuccessChip to AdminColors.Success
        PaymentStatus.FAILED, PaymentStatus.REFUND_FAILED -> AdminColors.PrimaryWash to AdminColors.Primary
        PaymentStatus.PENDING, PaymentStatus.INITIATED, PaymentStatus.REFUND_PENDING -> AdminColors.AmberWash to AdminColors.Amber
        PaymentStatus.ABANDONED -> AdminColors.Chip to AdminColors.Muted
    }
    DotBadge(status.name, bg, fg)
}

@Composable
private fun SeatBadge(status: SeatStatus) {
    val (bg, fg) = when (status) {
        SeatStatus.BOOKED -> AdminColors.SuccessChip to AdminColors.Success
        SeatStatus.HELD -> AdminColors.AmberWash to AdminColors.Amber
        SeatStatus.AVAILABLE -> AdminColors.Chip to AdminColors.Muted
    }
    Row(
        modifier = Modifier.backgroundColor(bg).borderRadius(6.px).padding(topBottom = 4.px, leftRight = 10.px),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        FaLock(Modifier.color(fg).fontSize(10.px))
        SpanText(status.name.titlecaseWord(), Modifier.color(fg).fontSize(11.px).fontWeight(FontWeight.SemiBold))
    }
}

// ---- Page-specific bits ---------------------------------------------------------------------

@Composable
private fun Avatar(text: String) {
    Box(
        modifier = Modifier.size(26.px).backgroundColor(AdminColors.Chip).borderRadius(9999.px),
        contentAlignment = Alignment.Center,
    ) { SpanText(text, Modifier.color(AdminColors.Muted).fontSize(10.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
private fun ExportButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaDownload(Modifier.color(AdminColors.Heading).fontSize(13.px))
        SpanText("Export", Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 9.px, leftRight = 14.px).cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaArrowLeft(Modifier.color(AdminColors.Heading).fontSize(12.px))
        SpanText("Back to Reservations", Modifier.color(AdminColors.Heading).fontSize(13.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun CancelButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .thenIf(!enabled) { Modifier.opacity(0.5) }
            .thenIf(enabled) { Modifier.cursor(Cursor.Pointer).onClick { onClick() } },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

// ---- helpers ------------------------------------------------------------------------------

/** Client-side CSV of the currently filtered rows (a real export endpoint is a planned phase). */
private fun exportCsv(state: AdminReservationsState, rows: List<Reservation>) {
    val header = listOf("ID", "User ID", "Movie", "Showtime", "Room", "Seats", "Total (NGN)", "Status", "Created At")
    val lines = rows.map { r ->
        val showtime = state.showtime(r.showtimeId)
        listOf(
            "RSV-${r.id}",
            "USR-${r.userId}",
            showtime?.let { state.movie(it.movieId)?.title } ?: "Showtime #${r.showtimeId}",
            showtime?.startsAt?.toString() ?: "",
            showtime?.let { st -> state.room(st.roomId)?.name } ?: "",
            "${r.seats.size}",
            "${r.totalAmount}",
            r.status.name,
            r.createdAt.toString(),
        )
    }
    val csv = (listOf(header) + lines).joinToString("\n") { row ->
        row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }
    val blob = Blob(arrayOf(csv), BlobPropertyBag(type = "text/csv;charset=utf-8;"))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = "reservations.csv"
    anchor.click()
    URL.revokeObjectURL(url)
}

