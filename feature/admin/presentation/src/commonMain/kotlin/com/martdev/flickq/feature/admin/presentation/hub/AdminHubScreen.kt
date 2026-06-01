package com.martdev.flickq.feature.admin.presentation.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martdev.flickq.core.designsystem.AdminScaffold
import com.martdev.flickq.core.designsystem.FlickQColors

/** The admin landing hub: a list of cards that fan out to each management area. */
@Composable
fun AdminHubScreen(
    onOpenReports: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenGenres: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenShowtimes: () -> Unit,
    onOpenReservations: () -> Unit,
) {
    AdminScaffold(title = "FlickQ Admin") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HubCard("Reports", "Revenue & capacity insights", onOpenReports) }
            item { HubCard("Movies", "Create, edit & remove films", onOpenMovies) }
            item { HubCard("Genres", "Manage the genre catalogue", onOpenGenres) }
            item { HubCard("Rooms", "Screens & seat layouts", onOpenRooms) }
            item { HubCard("Showtimes", "Schedule & status", onOpenShowtimes) }
            item { HubCard("Reservations", "Bookings & payments", onOpenReservations) }
        }
    }
}

@Composable
private fun HubCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlickQColors.DeepNavy, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, color = FlickQColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = FlickQColors.SeatAvailable, fontSize = 13.sp)
    }
}
