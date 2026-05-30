package com.martdev.flickq.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Palette lifted from the CinemaBookingExperience reference. */
object FlickQColors {
    val DeepNavy = Color(0xFF16213E)
    val AlmostBlack = Color(0xFF080B12)
    val Black = Color(0xFF000000)

    val GoldHighlight = Color(0xFFFFD166)
    val Gold = Color(0xFFF4C430)
    val GoldEdge = Color(0xFFD4AF37)

    val SeatAvailable = Color(0xFF64748B)
    val SeatOccupied = Color(0xFF1E293B)

    val SurfaceNavy = Color(0xFF1B2A4A)
    val OutlineNavy = Color(0xFF2C3E63)

    val TicketPaper = Color(0xFFF4F4F6)
    val TicketText = Color(0xFF1D1D1F)
    val TicketSubtitle = Color(0xFF86868B)

    val Error = Color(0xFFEF4444)
    val OnGold = AlmostBlack
}

val PremiumGoldGradient = Brush.linearGradient(
    colors = listOf(FlickQColors.GoldHighlight, FlickQColors.Gold, FlickQColors.GoldEdge)
)

val RoomBackgroundBrush = Brush.radialGradient(
    colors = listOf(FlickQColors.DeepNavy, FlickQColors.AlmostBlack, FlickQColors.Black),
    radius = 2000f
)
