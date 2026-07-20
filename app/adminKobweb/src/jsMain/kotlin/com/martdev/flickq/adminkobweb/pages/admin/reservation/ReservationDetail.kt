package com.martdev.flickq.adminkobweb.pages.admin.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.DotBadge
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.FieldLabel
import com.martdev.flickq.adminkobweb.components.FormCard
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.Overlay
import com.martdev.flickq.adminkobweb.components.PrimaryButtonPlain
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.dispDate
import com.martdev.flickq.adminkobweb.components.dispTime
import com.martdev.flickq.adminkobweb.components.groupDigits
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.components.titlecaseWord
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailAction
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailState
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationDetailViewModel
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.SeatStatus
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
import com.varabyte.kobweb.compose.ui.modifiers.letterSpacing
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaArrowLeft
import com.varabyte.kobweb.silk.components.icons.fa.FaCircleInfo
import com.varabyte.kobweb.silk.components.icons.fa.FaCouch
import com.varabyte.kobweb.silk.components.icons.fa.FaLock
import com.varabyte.kobweb.silk.components.icons.fa.FaMoneyBill
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.koin.core.parameter.parametersOf

@Page("detail/{id}")
@Composable
fun ReservationDetailPage() {
    RequireAdmin {
        AdminLayout(AdminNav.Reservations, "Reservation") {
            ReservationDetailContent()
        }
    }
}

@Composable
fun ReservationDetailContent() {
    val ctx = rememberPageContext()
    val reservationId = ctx.route.params.getValue("id").toLongOrNull() ?: 0L

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
            BackButton {
                ctx.router.navigateTo("/admin/reservation/list")
            }
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