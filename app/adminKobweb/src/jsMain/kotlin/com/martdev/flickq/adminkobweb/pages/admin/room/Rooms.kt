package com.martdev.flickq.adminkobweb.pages.admin.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.Overlay
import com.martdev.flickq.adminkobweb.components.PrimaryButton
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
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
import com.martdev.flickq.room.model.Room
import com.varabyte.kobweb.browser.storage.setItem
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
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaDoorOpen
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px

@Page("list")
@Composable
fun RoomsPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Rooms, title = "Room") {
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
        when (event) {
            AdminRoomsEvent.NavigateToAddNewRoom -> ctx.router.navigateTo("/admin/room/info?mode=add")
            is AdminRoomsEvent.NavigateToEditRoom -> {
                sessionStorage.setItem("name", event.name)
                sessionStorage.setItem("rows", event.rows)
                sessionStorage.setItem("columns", event.columns)
                ctx.router.navigateTo("/admin/room/info?id=${event.id}&mode=edit")
            }

            is AdminRoomsEvent.NavigateToRoomDetail -> {
                val roomDataStorageKey = RoomDataStorageKey("room_data")
                sessionStorage.setItem(roomDataStorageKey, event.roomData)
                ctx.router.navigateTo("/admin/room/detail/${event.roomData.id}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        RoomListView(state, onAction)
        // Overlays sit above whichever view is showing.
        state.deleting?.let { room -> DeleteConfirm(room, onAction) }
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
            SpanText(
                "Showing ${state.rooms.size} rooms",
                Modifier.color(AdminColors.Body).fontSize(13.px)
            )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.px)
            ) {
                Box(
                    modifier = Modifier.size(32.px).backgroundColor(AdminColors.Chip)
                        .borderRadius(8.px),
                    contentAlignment = Alignment.Center,
                ) { FaDoorOpen(Modifier.color(AdminColors.Body).fontSize(13.px)) }
                SpanText(
                    room.name,
                    Modifier.color(AdminColors.Heading).fontSize(14.px)
                        .fontWeight(FontWeight.SemiBold)
                        .cursor(Cursor.Pointer)
                        .onClick { onAction(AdminRoomsAction.OnRoomClick(room)) },
                )
            }
        }
        Cell(140.px) {
            SpanText(
                "${room.rows} × ${room.columns}",
                Modifier.color(AdminColors.Body).fontSize(14.px)
            )
        }
        Cell(120.px) {
            SpanText(
                "${room.rows * room.columns}",
                Modifier.color(AdminColors.Body).fontSize(14.px)
            )
        }
        Cell(110.px) {
            IconButton(
                { FaPenToSquare(it) },
                AdminColors.Body
            ) { onAction(AdminRoomsAction.OnEditClick(room)) }
        }
    }
}

// ---- Confirm overlays ---------------------------------------------------------------------

@Composable
private fun DeleteConfirm(room: Room, onAction: (AdminRoomsAction) -> Unit) {
    Overlay {
        SpanText(
            "Delete room",
            Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px)
                .fontWeight(FontWeight.Bold)
        )
        SpanText(
            "“${room.name}” will be removed. Rooms with scheduled showtimes can't be deleted.",
            Modifier.color(AdminColors.Body).fontSize(14.px),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.px)
        ) {
            Box(modifier = Modifier.flexGrow(1)) {
                SecondaryButton("Cancel") {
                    onAction(
                        AdminRoomsAction.OnDismissDelete
                    )
                }
            }
            Box(modifier = Modifier.flexGrow(1)) {
                PrimaryButton("Delete") {
                    onAction(
                        AdminRoomsAction.OnConfirmDelete
                    )
                }
            }
        }
    }
}
