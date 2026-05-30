package com.martdev.flickq.feature.movie.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.movie.presentation.MovieUi
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MovieDetailRoot(
    movieId: Long,
    onNavigateBack: () -> Unit,
    onViewShowtimes: (Long) -> Unit,
    viewModel: MovieDetailViewModel = koinViewModel { parametersOf(movieId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MovieDetailEvent.NavigateBack -> onNavigateBack()
            is MovieDetailEvent.NavigateToShowtimes -> onViewShowtimes(event.movieId)
        }
    }

    MovieDetailScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun MovieDetailScreen(
    state: MovieDetailState,
    onAction: (MovieDetailAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = FlickQColors.Gold,
                modifier = Modifier.align(Alignment.Center)
            )

            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.error.asString(), color = FlickQColors.Error)
                FlickQButton(
                    text = "Retry",
                    onClick = { onAction(MovieDetailAction.OnRetry) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            state.movie != null -> MovieDetailContent(
                movie = state.movie,
                onBack = { onAction(MovieDetailAction.OnBackClick) },
                onSeeShowtimes = { onAction(MovieDetailAction.OnSeeShowtimesClick) }
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: MovieUi,
    onBack: () -> Unit,
    onSeeShowtimes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(text = "← Back", color = FlickQColors.GoldHighlight)
        }

        PosterImage(
            url = movie.posterUrl,
            contentDescription = movie.title,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
        )

        Text(
            text = movie.title,
            color = FlickQColors.Gold,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = listOf(movie.year, movie.duration).filter { it.isNotBlank() }.joinToString(" · "),
            color = FlickQColors.SeatAvailable,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (movie.genres.isNotBlank()) {
            Text(
                text = movie.genres,
                color = FlickQColors.GoldHighlight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            text = movie.description,
            color = FlickQColors.TicketPaper,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 20.dp)
        )

        FlickQButton(
            text = "See showtimes",
            onClick = onSeeShowtimes,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 280.dp)
                .padding(top = 28.dp)
        )
    }
}
