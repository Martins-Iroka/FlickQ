package com.martdev.flickq.adminkobweb.pages.admin.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.FieldLabel
import com.martdev.flickq.adminkobweb.components.FormCard
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SaveButton
import com.martdev.flickq.adminkobweb.components.SecondaryButtonInline
import com.martdev.flickq.adminkobweb.components.TextField
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminAddEditAction
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminAddEditEvent
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminAddEditRoomState
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminAddEditRoomViewModel
import com.martdev.flickq.feature.admin.presentation.logic.rooms.TheRoomForm
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
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.icons.fa.FaDoorOpen
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.koin.core.parameter.parametersOf

@Page("info")
@Composable
fun RoomPage() {
    RequireAdmin {
        AdminLayout(AdminNav.Rooms, title = "Room") {
            RoomContent()
        }
    }
}

@Composable
private fun RoomContent() {
    val ctx = rememberPageContext()
    val id = ctx.route.params["id"]?.toLongOrNull() ?: 0
    val mode = ctx.route.params["mode"].orEmpty()
    var name = ""
    var rows = ""
    var columns = ""
    if (mode == "edit") {
        name = sessionStorage.getItem("name").orEmpty()
        rows = sessionStorage.getItem("rows").orEmpty()
        columns = sessionStorage.getItem("columns").orEmpty()
    }
    val vm = rememberAdminViewModel<AdminAddEditRoomViewModel> {
        parametersOf(id, name, rows, columns)
    }
    val state by vm.state.collectAsState()
    val onAction = vm::onAction
    ObserveAsEvents(vm.event) { event ->
        when(event) {
            AdminAddEditEvent.NavigateToList -> {
                ctx.router.navigateTo("/admin/room/list", UpdateHistoryMode.REPLACE)
            }
        }
    }
    RoomFormView(state, state.form, onAction)
}

@Composable
private fun RoomFormView(
    state: AdminAddEditRoomState,
    form: TheRoomForm,
    onAction: (AdminAddEditAction) -> Unit
) {
    val editing = form.editingId != 0L
    val rows = form.rows.toIntOrNull() ?: 0
    val cols = form.columns.toIntOrNull() ?: 0
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
                SpanText(
                    if (editing) "Edit Room" else "Create New Room",
                    Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px)
                        .fontWeight(FontWeight.Bold),
                )
                SpanText(
                    "Configure layout and seating capacity.",
                    Modifier.color(AdminColors.Body).fontSize(14.px)
                )
            }
            SecondaryButtonInline("← Back to Rooms") {
                onAction(AdminAddEditAction.OnDismiss)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.px)
        ) {
            // Left: configuration.
            Column(modifier = Modifier.flexGrow(1).flexBasis(0.px).minWidth(320.px)) {
                FormCard("Room Configuration", { FaDoorOpen(it) }) {
                    FieldLabel("ROOM NAME")
                    TextField(
                        form.name,
                        "e.g., Theater 1 (IMAX)"
                    ) { onAction(AdminAddEditAction.OnNameChange(it)) }
                    Row(
                        modifier = Modifier.fillMaxWidth().margin(top = 8.px),
                        horizontalArrangement = Arrangement.spacedBy(16.px)
                    ) {
                        Column(
                            modifier = Modifier.flexGrow(1).flexBasis(0.px),
                            verticalArrangement = Arrangement.spacedBy(8.px)
                        ) {
                            FieldLabel("ROWS")
                            TextField(
                                form.rows,
                                "10"
                            ) { onAction(AdminAddEditAction.OnRowsChange(it)) }
                        }
                        Column(
                            modifier = Modifier.flexGrow(1).flexBasis(0.px),
                            verticalArrangement = Arrangement.spacedBy(8.px)
                        ) {
                            FieldLabel("COLUMNS")
                            TextField(
                                form.columns,
                                "12"
                            ) { onAction(AdminAddEditAction.OnColumnsChange(it)) }
                        }
                    }
                    // Total capacity.
                    Row(
                        modifier = Modifier.fillMaxWidth().margin(top = 8.px)
                            .backgroundColor(AdminColors.Surface)
                            .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px)
                            .padding(topBottom = 14.px, leftRight = 16.px),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpanText("Total Capacity", Modifier.color(AdminColors.Body).fontSize(14.px))
                        SpanText(
                            "${rows * cols}",
                            Modifier.color(AdminColors.BodyStrong).fontSize(20.px)
                                .fontWeight(FontWeight.Bold)
                        )
                    }
                    state.dialogError?.let {
                        SpanText(
                            it.plain(),
                            Modifier.color(AdminColors.Primary).fontSize(13.px).margin(top = 4.px)
                        )
                    }
                    /*Row(
                        modifier = Modifier.fillMaxWidth().margin(top = 8.px),
                        horizontalArrangement = Arrangement.spacedBy(12.px),
                    ) {
                        SecondaryButtonInline("Cancel") {
                            onAction(AdminAddEditAction.OnDismiss)
                        }
                        SaveButton(
                            label = if (state.isSaving) "Saving…" else "Save Room",
                            enabled = form.isValid && !state.isSaving,
                        ) { onAction(AdminAddEditAction.OnSave) }
                    }*/
                    SaveButton(
                        label = if (state.isSaving) "Saving…" else "Save Room",
                        enabled = form.isValid && !state.isSaving,
                    ) { onAction(AdminAddEditAction.OnSave) }
                }
            }
            // Right: live preview.
            Column(modifier = Modifier.flexGrow(2).flexBasis(0.px).minWidth(360.px)) {
                LayoutPreviewCard(rows, cols)
            }
        }
    }
}

@Composable
private fun LayoutPreviewCard(rows: Int, cols: Int) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px).padding(24.px),
        verticalArrangement = Arrangement.spacedBy(20.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                FaDoorOpen(Modifier.color(AdminColors.Primary).fontSize(16.px))
                SpanText("Live Layout Preview", Modifier.montserrat().color(AdminColors.Heading).fontSize(18.px).fontWeight(FontWeight.SemiBold))
            }
            LegendDot("Available", AdminColors.Chip)
        }
        ScreenBar()
        if (rows in 1..40 && cols in 1..30) {
            SeatGridPreview(rows, cols)
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(200.px), contentAlignment = Alignment.Center) {
                SpanText(
                    if (rows <= 0 || cols <= 0) "Enter rows and columns to preview the layout."
                    else "Layout too large to preview (max 40 × 30).",
                    Modifier.color(AdminColors.Muted).fontSize(13.px),
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, fill: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
        Box(Modifier.size(14.px).backgroundColor(fill).borderRadius(4.px))
        SpanText(label, Modifier.color(AdminColors.Body).fontSize(12.px))
    }
}

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
private fun SeatGridPreview(rows: Int, cols: Int) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .backgroundColor(AdminColors.BgDeep).borderRadius(12.px).padding(20.px),
        verticalArrangement = Arrangement.spacedBy(6.px),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.px)) {
                repeat(cols) {
                    Box(modifier = Modifier.size(18.px).backgroundColor(AdminColors.Chip).borderRadius(4.px))
                }
            }
        }
    }
}