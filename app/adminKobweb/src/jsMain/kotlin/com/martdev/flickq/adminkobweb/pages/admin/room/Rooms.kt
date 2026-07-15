package com.martdev.flickq.adminkobweb.pages.admin.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.FIELD_CSS
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.Overlay
import com.martdev.flickq.adminkobweb.components.PagerButton
import com.martdev.flickq.adminkobweb.components.PrimaryButton
import com.martdev.flickq.adminkobweb.components.PrimaryButtonPlain
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.SecondaryButtonInline
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsAction
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsEvent
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsState
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsViewModel
import com.martdev.flickq.feature.admin.presentation.logic.rooms.RoomDetail
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
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
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaDoorOpen
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Input

@Page("list")
@Composable
fun RoomsPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Rooms, title = "Rooms") {
            RoomsContent()
        }
    }
}

@Composable
private fun RoomsContent() {
    val ctx = rememberPageContext()
    val vm = rememberAdminViewModel<AdminRoomsViewModel>()
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    ObserveAsEvents(vm.event) { event ->
        when(event) {
            AdminRoomsEvent.AddRoom -> ctx.router.navigateTo("/admin/room/info?mode=add")
            is AdminRoomsEvent.EditRoom -> {
                sessionStorage.setItem("name", event.name)
                sessionStorage.setItem("rows", event.rows)
                sessionStorage.setItem("columns", event.columns)
                ctx.router.navigateTo("/admin/room/info?id=${event.id}&mode=edit")
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val detail = state.detail
        when {
            detail != null -> RoomDetailView(state, detail, onAction)
            else -> RoomListView(state, onAction)
        }
        // Overlays sit above whichever view is showing.
        state.deleting?.let { room -> DeleteConfirm(room, onAction) }
        state.seatingFor?.let { room -> GenerateSeatsConfirm(room, onAction) }
    }
}

// ---- List ---------------------------------------------------------------------------------

@Composable
private fun RoomListView(state: AdminRoomsState, onAction: (AdminRoomsAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText(
                "Configure layout and seating capacity.",
                Modifier.color(AdminColors.Body).fontSize(14.px),
            )
            PrimaryButton("Add Room") { onAction(AdminRoomsAction.OnAddClick) }
        }

        state.message?.let { msg ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .backgroundColor(AdminColors.SuccessWash)
                    .border(1.px, LineStyle.Solid, AdminColors.Success)
                    .borderRadius(8.px).padding(topBottom = 10.px, leftRight = 14.px),
            ) {
                SpanText(msg.plain(), Modifier.color(AdminColors.Success).fontSize(13.px))
            }
        }

        val error = state.error
        when {
            state.isLoading -> StatusBox("Loading rooms…")
            error != null -> ErrorBox(error) { onAction(AdminRoomsAction.OnRetry) }
            state.rooms.isEmpty() -> StatusBox("No rooms yet. Tap “Add Room” to create one.")
            else -> RoomTable(state, onAction)
        }
    }
}

@Composable
private fun RoomTable(state: AdminRoomsState, onAction: (AdminRoomsAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Room Name", null)
            HeaderCell("Layout", 140.px)
            HeaderCell("Capacity", 120.px)
            HeaderCell("Actions", 110.px)
        }
        state.rooms.forEach { room -> RoomRow(room, onAction) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpanText("Showing ${state.rooms.size} rooms", Modifier.color(AdminColors.Body).fontSize(13.px))
        }
    }
}

@Composable
private fun RoomRow(room: Room, onAction: (AdminRoomsAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                Box(
                    modifier = Modifier.size(32.px).backgroundColor(AdminColors.Chip).borderRadius(8.px),
                    contentAlignment = Alignment.Center,
                ) { FaDoorOpen(Modifier.color(AdminColors.Body).fontSize(13.px)) }
                SpanText(
                    room.name,
                    Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)
                        .cursor(Cursor.Pointer).onClick { onAction(AdminRoomsAction.OnRoomClick(room)) },
                )
            }
        }
        Cell(140.px) {
            SpanText("${room.rows} × ${room.columns}", Modifier.color(AdminColors.Body).fontSize(14.px))
        }
        Cell(120.px) {
            SpanText("${room.rows * room.columns}", Modifier.color(AdminColors.Body).fontSize(14.px))
        }
        Cell(110.px) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ FaPenToSquare(it) }, AdminColors.Body) { onAction(AdminRoomsAction.OnEditClick(room)) }
                IconButton({ FaTrash(it) }, AdminColors.Primary) { onAction(AdminRoomsAction.OnDeleteClick(room)) }
            }
        }
    }
}

// ---- Detail (seat map + inventory) --------------------------------------------------------

@Composable
private fun RoomDetailView(state: AdminRoomsState, detail: RoomDetail, onAction: (AdminRoomsAction) -> Unit) {
    val room = detail.room
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(20.px),
    ) {
        SpanText(
            "← Back to Rooms",
            Modifier.color(AdminColors.Body).fontSize(13.px).fontWeight(FontWeight.SemiBold)
                .cursor(Cursor.Pointer).onClick { onAction(AdminRoomsAction.OnCloseDetail) },
        )
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText(room.name, Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold))
                SpanText("Room ID: RM-${room.id} • Standard Configuration", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                SecondaryButtonInline("Edit Room") { onAction(AdminRoomsAction.OnEditClick(room)) }
                PrimaryButtonPlain("Generate Seats") { onAction(AdminRoomsAction.OnGenerateSeatsClick(room)) }
            }
        }

        state.message?.let { msg ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .backgroundColor(AdminColors.SuccessWash)
                    .border(1.px, LineStyle.Solid, AdminColors.Success)
                    .borderRadius(8.px).padding(topBottom = 10.px, leftRight = 14.px),
            ) { SpanText(msg.plain(), Modifier.color(AdminColors.Success).fontSize(13.px)) }
        }

        // Stat cards.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.px)) {
            StatCard("TOTAL ROWS", "${room.rows}")
            StatCard("COLUMNS", "${room.columns}")
            StatCard("TOTAL CAPACITY", "${room.rows * room.columns}")
            StatusCard(detail)
        }

        // Seat map.
        SeatMapCard(detail, onAction)

        // Inventory.
        SeatInventoryCard(detail)
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        modifier = Modifier.flexGrow(1).flexBasis(0.px)
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px).padding(20.px),
        verticalArrangement = Arrangement.spacedBy(8.px),
    ) {
        SpanText(label, Modifier.color(AdminColors.Muted).fontSize(11.px).fontWeight(FontWeight.SemiBold))
        SpanText(value, Modifier.montserrat().color(AdminColors.BodyStrong).fontSize(28.px).fontWeight(FontWeight.Bold))
    }
}

@Composable
private fun StatusCard(detail: RoomDetail) {
    Column(
        modifier = Modifier.flexGrow(1).flexBasis(0.px)
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px).padding(20.px),
        verticalArrangement = Arrangement.spacedBy(8.px),
    ) {
        SpanText("STATUS", Modifier.color(AdminColors.Muted).fontSize(11.px).fontWeight(FontWeight.SemiBold))
        val (label, tint) = when {
            detail.isLoadingSeats -> "Loading…" to AdminColors.Muted
            detail.hasSeats -> "Seats Generated" to AdminColors.Success
            else -> "No Seats" to AdminColors.Amber
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px), modifier = Modifier.margin(top = 6.px)) {
            Box(Modifier.size(9.px).backgroundColor(tint).borderRadius(9999.px))
            SpanText(label, Modifier.color(tint).fontSize(16.px).fontWeight(FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SeatMapCard(detail: RoomDetail, onAction: (AdminRoomsAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px).padding(24.px),
        verticalArrangement = Arrangement.spacedBy(20.px),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenBar()
        val seatsError = detail.seatsError
        when {
            detail.isLoadingSeats -> StatusBox("Loading seats…")
            seatsError != null -> ErrorBox(seatsError) { onAction(AdminRoomsAction.OnRetrySeats) }
            detail.seats.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(32.px), contentAlignment = Alignment.Center) {
                SpanText("No seats generated yet. Use “Generate Seats” to build the layout.", Modifier.color(AdminColors.Muted).fontSize(14.px))
            }
            else -> SeatMapGrid(detail.seats)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.px), verticalAlignment = Alignment.CenterVertically) {
            LegendDot("Standard Seat", AdminColors.Chip)
        }
    }
}

@Composable
private fun SeatMapGrid(seats: List<Seat>) {
    // Group by row label, preserving generation order; sort seats within a row by number.
    val byRow = seats.groupBy { it.rowLabel }
    Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
        byRow.forEach { (label, rowSeats) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                RowLabelBox(label)
                Row(horizontalArrangement = Arrangement.spacedBy(6.px)) {
                    rowSeats.sortedBy { it.seatNumber }.forEach { seat -> SeatCell(seat.seatNumber) }
                }
                RowLabelBox(label)
            }
        }
    }
}

@Composable
private fun RowLabelBox(label: String) {
    Box(modifier = Modifier.width(24.px), contentAlignment = Alignment.Center) {
        SpanText(label, Modifier.color(AdminColors.Body).fontSize(13.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun SeatCell(number: Int) {
    Box(
        modifier = Modifier.size(26.px).backgroundColor(AdminColors.Chip).borderRadius(5.px),
        contentAlignment = Alignment.Center,
    ) {
        SpanText("$number", Modifier.color(AdminColors.Muted).fontSize(10.px))
    }
}

@Composable
private fun SeatInventoryCard(detail: RoomDetail) {
    if (detail.isLoadingSeats || detail.seats.isEmpty()) return

    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    val pageSize = 10

    val filtered = remember(query, detail.seats) {
        if (query.isBlank()) detail.seats
        else detail.seats.filter { seat ->
            val code = seatCode(seat)
            query.lowercase() in code.lowercase() ||
                query in seat.seatNumber.toString() ||
                query.lowercase() in seat.rowLabel.lowercase()
        }
    }
    val total = filtered.size
    val lastPage = if (total == 0) 0 else (total - 1) / pageSize
    val safePage = page.coerceIn(0, lastPage)
    val start = safePage * pageSize
    val visible = filtered.drop(start).take(pageSize)

    Column(
        modifier = Modifier.fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText("Seat Inventory Reference", Modifier.montserrat().color(AdminColors.Heading).fontSize(18.px).fontWeight(FontWeight.SemiBold))
            Box(modifier = Modifier.width(240.px)) {
                Input(type = InputType.Text) {
                    value(query)
                    attr("placeholder", "Search seats…")
                    attr("style", FIELD_CSS)
                    onInput { query = it.value; page = 0 }
                }
            }
        }
        // Column header.
        Row(
            modifier = Modifier.fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
                .padding(topBottom = 12.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Row", 120.px)
            HeaderCell("Seat Number", null)
            HeaderCell("Seat Code", null)
            HeaderCell("Type", null)
            HeaderCell("Status", 120.px)
        }
        visible.forEach { seat -> SeatInventoryRow(seat) }
        // Footer / pagination.
        Row(
            modifier = Modifier.fillMaxWidth().padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val from = if (total == 0) 0 else start + 1
            val to = start + visible.size
            SpanText("Showing $from to $to of $total entries", Modifier.color(AdminColors.Body).fontSize(13.px))
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                PagerButton("Prev", enabled = safePage > 0) { page = safePage - 1 }
                SpanText("${safePage + 1} / ${lastPage + 1}", Modifier.color(AdminColors.Body).fontSize(13.px))
                PagerButton("Next", enabled = safePage < lastPage) { page = safePage + 1 }
            }
        }
    }
}

@Composable
private fun SeatInventoryRow(seat: Seat) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(120.px) { SpanText(seat.rowLabel, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
        Cell(null) { SpanText("${seat.seatNumber}", Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(null) { SpanText(seatCode(seat), Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(null) { SpanText("Standard", Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(120.px) {
            Row(
                modifier = Modifier.backgroundColor(AdminColors.SuccessChip).borderRadius(9999.px).padding(topBottom = 3.px, leftRight = 10.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpanText("Active", Modifier.color(AdminColors.Success).fontSize(12.px).fontWeight(FontWeight.SemiBold))
            }
        }
    }
}

// ---- Confirm overlays ---------------------------------------------------------------------

@Composable
private fun DeleteConfirm(room: Room, onAction: (AdminRoomsAction) -> Unit) {
    Overlay {
        SpanText("Delete room", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText(
            "“${room.name}” will be removed. Rooms with scheduled showtimes can't be deleted.",
            Modifier.color(AdminColors.Body).fontSize(14.px),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminRoomsAction.OnDismissDelete) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButton("Delete") { onAction(AdminRoomsAction.OnConfirmDelete) } }
        }
    }
}

@Composable
private fun GenerateSeatsConfirm(room: Room, onAction: (AdminRoomsAction) -> Unit) {
    Overlay {
        SpanText("Generate seats", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText(
            "This creates ${room.rows * room.columns} seats (${room.rows} × ${room.columns}) for “${room.name}”. Existing seats are replaced.",
            Modifier.color(AdminColors.Body).fontSize(14.px),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminRoomsAction.OnDismissGenerateSeats) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButtonPlain("Generate") { onAction(AdminRoomsAction.OnConfirmGenerateSeats) } }
        }
    }
}

// ---- Page-specific bits ---------------------------------------------------------------------

@Composable
private fun ScreenBar() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.px)) {
        Box(
            modifier = Modifier.fillMaxWidth().maxWidth(640.px).height(8.px)
                .styleModifier {
                    property("background", "linear-gradient(90deg, rgba(229,9,20,0) 0%, rgba(229,9,20,0.6) 50%, rgba(229,9,20,0) 100%)")
                    property("border-radius", "9999px")
                },
        )
        SpanText("SCREEN", Modifier.color(AdminColors.Muted).fontSize(11.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun LegendDot(label: String, fill: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
        Box(Modifier.size(14.px).backgroundColor(fill).borderRadius(4.px))
        SpanText(label, Modifier.color(AdminColors.Body).fontSize(12.px))
    }
}

// ---- helpers ------------------------------------------------------------------------------

private fun seatCode(seat: Seat): String = "${seat.rowLabel}-${seat.seatNumber.toString().padStart(2, '0')}"
