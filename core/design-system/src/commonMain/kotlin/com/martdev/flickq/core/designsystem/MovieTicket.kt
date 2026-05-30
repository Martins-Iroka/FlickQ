package com.martdev.flickq.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random

private val TicketCornerRadius = 28.dp
private val TicketSideNotchRadius = 12.dp
private val TicketHalfHeight = 220.dp

private val QrGoldBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFFD166), Color(0xFFF4C430), Color(0xFFD4AF37)),
    start = Offset.Zero,
    end = Offset(x = 500f, y = 500f)
)

private val TicketPaperBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFFFFFFFF), FlickQColors.TicketPaper)
)

/**
 * A self-contained cinema ticket stub, adapted KMP-safely from the
 * CinemaBookingExperience reference (perforated halves, generative gold QR,
 * dashed perforation). Animation / shared-element morph code is intentionally
 * dropped — this is a static, themed ticket.
 */
@Composable
fun MovieTicket(
    movieTitle: String,
    runtimeLabel: String,
    seatLabel: String,
    dateLabel: String,
    hallTimeLabel: String,
    code: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TicketTopHalf(
            movieTitle = movieTitle,
            runtimeLabel = runtimeLabel,
            modifier = Modifier.fillMaxWidth().height(TicketHalfHeight)
        )
        TicketBottomHalf(
            seatLabel = seatLabel,
            dateLabel = dateLabel,
            hallTimeLabel = hallTimeLabel,
            code = code,
            modifier = Modifier.fillMaxWidth().height(TicketHalfHeight)
        )
    }
}

@Composable
private fun TicketTopHalf(
    movieTitle: String,
    runtimeLabel: String,
    modifier: Modifier = Modifier
) {
    val shape = remember { PerforatedHalfShape(TicketCornerRadius, TicketSideNotchRadius, NotchedEdge.BOTTOM) }
    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.6f), shape = shape)
            .clip(shape = shape)
            .background(brush = TicketPaperBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                GenerativeGradientQrCode(modifier = Modifier.size(120.dp))
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = movieTitle,
                    color = FlickQColors.TicketText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = runtimeLabel,
                    color = FlickQColors.TicketSubtitle,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        RealisticPerforationEffect(modifier = Modifier.align(Alignment.BottomCenter), isTopHalf = true)
    }
}

@Composable
private fun TicketBottomHalf(
    seatLabel: String,
    dateLabel: String,
    hallTimeLabel: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val shape = remember { PerforatedHalfShape(TicketCornerRadius, TicketSideNotchRadius, NotchedEdge.TOP) }
    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.6f), shape = shape)
            .clip(shape = shape)
            .background(brush = TicketPaperBrush)
    ) {
        RealisticPerforationEffect(modifier = Modifier.align(Alignment.TopCenter), isTopHalf = false)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ADMIT ONE",
                color = FlickQColors.GoldEdge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 38.dp, height = 34.dp), contentAlignment = Alignment.Center) {
                    SeatChip(modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = seatLabel,
                    color = FlickQColors.TicketText,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(text = "DATE", color = FlickQColors.TicketSubtitle, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = dateLabel, color = FlickQColors.TicketText, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "HALL · TIME", color = FlickQColors.TicketSubtitle, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = hallTimeLabel, color = FlickQColors.TicketText, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = code,
                color = FlickQColors.TicketSubtitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeatChip(modifier: Modifier = Modifier) {
    val uPath = remember { Path() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cornerX = (6f / 38f) * w
        val cornerY = (6f / 34f) * h
        val strokeW = (4f / 38f) * w
        uPath.reset()
        uPath.moveTo(0f, 0f)
        uPath.lineTo(0f, h - cornerY)
        uPath.quadraticTo(0f, h, cornerX, h)
        uPath.lineTo(w - cornerX, h)
        uPath.quadraticTo(w, h, w, h - cornerY)
        uPath.lineTo(w, 0f)
        drawPath(path = uPath, brush = PremiumGoldGradient, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        val cushionInset = (6f / 38f) * w
        drawRoundRect(
            brush = PremiumGoldGradient,
            topLeft = Offset(x = cushionInset, y = h - (14f / 34f) * h),
            size = Size(width = w - 2f * cushionInset, height = (10f / 34f) * h),
            cornerRadius = CornerRadius(x = (2f / 38f) * w, y = (2f / 34f) * h)
        )
    }
}

private const val QrGridSize = 25
private const val QrFinderSize = 8
private const val QrSeed = 12345L

@Composable
private fun GenerativeGradientQrCode(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val dimension = minOf(size.width, size.height)
            val cellSize = dimension / QrGridSize
            val offsetX = (size.width - dimension) / 2f
            val offsetY = (size.height - dimension) / 2f
            val r = Random(seed = QrSeed)

            val gridPoints = mutableListOf<Offset>()
            for (row in 0 until QrGridSize) {
                for (col in 0 until QrGridSize) {
                    val inTopLeft = row < QrFinderSize && col < QrFinderSize
                    val inTopRight = row < QrFinderSize && col >= QrGridSize - QrFinderSize
                    val inBottomLeft = row >= QrGridSize - QrFinderSize && col < QrFinderSize
                    if (!inTopLeft && !inTopRight && !inBottomLeft && r.nextBoolean()) {
                        gridPoints.add(Offset(x = offsetX + col * cellSize + 1.5f, y = offsetY + row * cellSize + 1.5f))
                    }
                }
            }
            val cellGlyphSize = Size(width = cellSize - 3f, height = cellSize - 3f)
            val cellCorner = CornerRadius(x = 4f, y = 4f)

            onDrawBehind {
                fun drawEye(row: Int, col: Int) {
                    val origin = Offset(x = offsetX + col * cellSize, y = offsetY + row * cellSize)
                    val eyeSize = 7 * cellSize
                    drawRoundRect(brush = QrGoldBrush, topLeft = origin, size = Size(eyeSize, eyeSize), cornerRadius = CornerRadius(12f, 12f))
                    drawRect(color = Color.White, topLeft = origin + Offset(cellSize, cellSize), size = Size(eyeSize - 2 * cellSize, eyeSize - 2 * cellSize))
                    drawRoundRect(brush = QrGoldBrush, topLeft = origin + Offset(2 * cellSize, 2 * cellSize), size = Size(eyeSize - 4 * cellSize, eyeSize - 4 * cellSize), cornerRadius = CornerRadius(6f, 6f))
                }
                drawEye(0, 0)
                drawEye(0, QrGridSize - 7)
                drawEye(QrGridSize - 7, 0)
                gridPoints.forEach { point ->
                    drawRoundRect(brush = QrGoldBrush, topLeft = point, size = cellGlyphSize, cornerRadius = cellCorner)
                }
            }
        }
    )
}

private val PerforationDashEffect: PathEffect =
    PathEffect.dashPathEffect(intervals = floatArrayOf(16f, 16f), phase = 0f)
private val PerforationShadowColor = Color(0xFF0A0D14)

@Composable
private fun RealisticPerforationEffect(modifier: Modifier = Modifier, isTopHalf: Boolean) {
    val density = LocalDensity.current.density
    val snR = TicketSideNotchRadius.value * density
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawWithCache {
                val yOffset = if (isTopHalf) size.height else 0f
                val startX = snR
                val endX = size.width - snR
                onDrawBehind {
                    drawLine(color = Color.White, start = Offset(startX, yOffset + 2f), end = Offset(endX, yOffset + 2f), strokeWidth = 8f, cap = StrokeCap.Round, pathEffect = PerforationDashEffect)
                    drawLine(color = PerforationShadowColor, start = Offset(startX, yOffset), end = Offset(endX, yOffset), strokeWidth = 8f, cap = StrokeCap.Round, pathEffect = PerforationDashEffect)
                }
            }
    )
}

private enum class NotchedEdge { TOP, BOTTOM }

private class PerforatedHalfShape(
    private val cornerRadius: Dp,
    private val sideNotchRadius: Dp,
    private val notchedEdge: NotchedEdge
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cR = with(density) { cornerRadius.toPx() }
        val snR = with(density) { sideNotchRadius.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            when (notchedEdge) {
                NotchedEdge.TOP -> {
                    moveTo(0f, snR)
                    arcTo(Rect(-snR, -snR, snR, snR), 90f, -90f, false)
                    lineTo(w - snR, 0f)
                    arcTo(Rect(w - snR, -snR, w + snR, snR), 180f, -90f, false)
                    lineTo(w, h - cR)
                    arcTo(Rect(w - 2f * cR, h - 2f * cR, w, h), 0f, 90f, false)
                    lineTo(cR, h)
                    arcTo(Rect(0f, h - 2f * cR, 2f * cR, h), 90f, 90f, false)
                    close()
                }

                NotchedEdge.BOTTOM -> {
                    moveTo(0f, cR)
                    arcTo(Rect(0f, 0f, 2f * cR, 2f * cR), 180f, 90f, false)
                    lineTo(w - cR, 0f)
                    arcTo(Rect(w - 2f * cR, 0f, w, 2f * cR), 270f, 90f, false)
                    lineTo(w, h - snR)
                    arcTo(Rect(w - snR, h - snR, w + snR, h + snR), 270f, -90f, false)
                    lineTo(snR, h)
                    arcTo(Rect(-snR, h - snR, snR, h + snR), 0f, -90f, false)
                    close()
                }
            }
        }
        return Outline.Generic(path)
    }
}
