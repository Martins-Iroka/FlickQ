package com.martdev.flickq.adminkobweb.pages.admin.reservation

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.components.DotBadge
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.reservation.model.ReservationStatus


@Composable
internal fun ReservationBadge(status: ReservationStatus) {
    val (bg, fg) = when (status) {
        ReservationStatus.PENDING -> AdminColors.AmberWash to AdminColors.Amber
        ReservationStatus.CONFIRMED -> AdminColors.SuccessChip to AdminColors.Success
        ReservationStatus.CANCELLED -> AdminColors.PrimaryWash to AdminColors.Primary
    }
    DotBadge(status.name, bg, fg)
}