package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush

@Composable
fun MyReservationScreen() {
    Column(modifier = Modifier.fillMaxSize()
        .background(RoomBackgroundBrush)) {

        Text(
            text = "My Reservation",
            color = FlickQColors.Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(3) {
                ReservationCard()
            }
        }
    }
}

@Composable
private fun ReservationCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        PosterImage(
            url = "https://www.themoviedb.org/t/p/w600_and_h900_face/zKVgiv5qHCvCLT4A2ymJi5QeXDH.jpg",
            contentDescription = "movie",
            modifier = Modifier
                .size(150.dp, 200.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Column(modifier = Modifier.fillMaxSize().padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Movie title title title",
                color = FlickQColors.TicketPaper,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "#5,000",
                color = FlickQColors.TicketPaper,
                fontSize = 18.sp
            )

            Text(
                text = "CONFIRMED",
                color = FlickQColors.Green,
                fontSize = 16.sp
            )
            
            Text(
                text = "expires",
                color = FlickQColors.TicketPaper
            )

            FlickQButton(
                text = "Pay",
                onClick = {},
            )
        }
    }
}