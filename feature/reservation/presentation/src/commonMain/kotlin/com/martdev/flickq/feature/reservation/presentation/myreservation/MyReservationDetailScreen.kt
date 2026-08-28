package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush

@Composable
fun MyReservationDetailScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        MyReservationDetail()
    }

}

@Composable
fun MyReservationDetail() {
    Column(modifier = Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        PosterImage(
            url = "",
            contentDescription = "",
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
        )

        Text(
            text = "Movie title",
            color = FlickQColors.Gold,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = listOf("starts at - 12:00pm", "ends at - 2:00pm").filter { it.isNotBlank() }.joinToString(" : "),
            color = FlickQColors.TicketPaper,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Showing at Room 5",
            color = FlickQColors.SeatAvailable,
            fontSize = 14.sp
        )

        Text(
            text = "Seat(s) - A5",
            color = FlickQColors.TicketPaper,
            fontSize = 16.sp
        )

        Text(
            text = "Status - Booked",
            color = FlickQColors.Green,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Expires at 12:15pm",
            color = FlickQColors.Error,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        FlickQButton(
            text = "Pay Now",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}