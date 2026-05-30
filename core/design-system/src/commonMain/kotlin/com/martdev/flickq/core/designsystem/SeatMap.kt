package com.martdev.flickq.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

/** One seat positioned on the room grid. [rowIndex]/[colIndex] are zero-based. */
data class SeatLayout(
    val id: Long,
    val rowIndex: Int,
    val colIndex: Int,
    val rowLabel: String,
    val seatNumber: Int,
    val occupied: Boolean
)

/**
 * Renders a theater seat grid on a [Canvas], adapted KMP-safely from the
 * CinemaBookingExperience reference (the U-shaped seat path + cushion + gold
 * selection). Selection state is owned by the caller (passed in [selectedIds]);
 * taps are reported via [onSeatClick]. Occupied seats are not tappable.
 */
@Composable
fun SeatMap(
    rows: Int,
    columns: Int,
    seats: List<SeatLayout>,
    selectedIds: Set<Long>,
    onSeatClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(rows, columns, seats) {
            detectTapGestures { tap ->
                val geom = gridGeometry(size.width.toFloat(), size.height.toFloat(), rows, columns)
                if (geom.cell <= 0f) return@detectTapGestures
                val col = ((tap.x - geom.originX) / geom.cell).toInt()
                val row = ((tap.y - geom.originY) / geom.cell).toInt()
                val seat = seats.firstOrNull { it.rowIndex == row && it.colIndex == col }
                if (seat != null && !seat.occupied) onSeatClick(seat.id)
            }
        }
    ) {
        val geom = gridGeometry(size.width, size.height, rows, columns)
        if (geom.cell <= 0f) return@Canvas

        drawScreenArc(geom, columns)

        val seatW = geom.cell * 0.66f
        val seatH = geom.cell * 0.60f
        seats.forEach { seat ->
            val center = Offset(
                x = geom.originX + geom.cell * seat.colIndex + geom.cell / 2f,
                y = geom.originY + geom.cell * seat.rowIndex + geom.cell / 2f
            )
            drawSeat(
                center = center,
                seatW = seatW,
                seatH = seatH,
                selected = seat.id in selectedIds,
                occupied = seat.occupied
            )
        }
    }
}

private data class GridGeometry(val cell: Float, val originX: Float, val originY: Float)

private const val ScreenReservePx = 56f

private fun gridGeometry(width: Float, height: Float, rows: Int, columns: Int): GridGeometry {
    if (rows <= 0 || columns <= 0 || width <= 0f || height <= 0f) return GridGeometry(0f, 0f, 0f)
    val usableHeight = height - ScreenReservePx
    val cell = min(width / columns, usableHeight / rows)
    val originX = (width - cell * columns) / 2f
    val originY = ScreenReservePx + (usableHeight - cell * rows) / 2f
    return GridGeometry(cell, originX, originY)
}

private fun DrawScope.drawScreenArc(geom: GridGeometry, columns: Int) {
    val screenY = ScreenReservePx * 0.55f
    val left = geom.originX
    val right = geom.originX + geom.cell * columns
    val path = Path().apply {
        moveTo(left, screenY)
        quadraticTo((left + right) / 2f, screenY - 26f, right, screenY)
    }
    drawPath(
        path = path,
        color = FlickQColors.Gold.copy(alpha = 0.55f),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSeat(
    center: Offset,
    seatW: Float,
    seatH: Float,
    selected: Boolean,
    occupied: Boolean
) {
    val left = center.x - seatW / 2f
    val top = center.y - seatH / 2f
    val cornerX = (6f / 38f) * seatW
    val cornerY = (6f / 34f) * seatH
    val strokeW = (4f / 38f) * seatW

    val path = Path().apply {
        moveTo(left, top)
        lineTo(left, top + seatH - cornerY)
        quadraticTo(left, top + seatH, left + cornerX, top + seatH)
        lineTo(left + seatW - cornerX, top + seatH)
        quadraticTo(left + seatW, top + seatH, left + seatW, top + seatH - cornerY)
        lineTo(left + seatW, top)
    }

    val cushionInset = (6f / 38f) * seatW
    val cushionTopLeft = Offset(x = left + cushionInset, y = top + seatH - (14f / 34f) * seatH)
    val cushionSize = Size(width = seatW - 2f * cushionInset, height = (10f / 34f) * seatH)
    val cushionCorner = CornerRadius(x = (2f / 38f) * seatW, y = (2f / 34f) * seatH)

    if (selected) {
        drawPath(path = path, brush = PremiumGoldGradient, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        drawRoundRect(brush = PremiumGoldGradient, topLeft = cushionTopLeft, size = cushionSize, cornerRadius = cushionCorner)
    } else {
        val color = if (occupied) FlickQColors.SeatOccupied else FlickQColors.SeatAvailable
        drawPath(path = path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        drawRoundRect(color = color, topLeft = cushionTopLeft, size = cushionSize, cornerRadius = cushionCorner)
    }
}
