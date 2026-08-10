package com.martdev.flickq.feature.movie.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.movie.presentation.MovieUi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun MovieListRoot(
    onMovieClick: (Long) -> Unit,
    onLogout: () -> Unit,
    viewModel: MovieListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MovieListEvent.NavigateToDetail -> onMovieClick(event.movieId)
        }
    }

    MovieListScreen(state = state, onAction = viewModel::onAction, onLogout = onLogout)
}

@Composable
fun MovieListScreen(
    state: MovieListState,
    onAction: (MovieListAction) -> Unit,
    onLogout: () -> Unit = {},
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )
    var showDatePicker by rememberSaveable {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FlickQ",
                color = FlickQColors.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            TextButton(onClick = onLogout) {
                Text(text = "Log out", color = FlickQColors.GoldHighlight, fontSize = 13.sp)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            FilterChip(
                state.isToday,
                onClick = {
                    onAction(MovieListAction.OnTodayClick)
                },
                label = {
                    Text(
                        "Today",
                        color = FlickQColors.Gold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            )

            FilterChip(
                state.isTomorrow,
                onClick = {
                    onAction(MovieListAction.OnTomorrowClick)
                },
                label = {
                    Text(
                        "Tomorrow",
                        color = FlickQColors.Gold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            )

            FilterChip(
                state.isToday.not() and state.isTomorrow.not(),
                onClick = {
                    showDatePicker = true
                },
                label = {
                    Text(
                        "Pick a date",
                        color = FlickQColors.Gold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(onDismissRequest = {
                showDatePicker = false
            },
                confirmButton = {
                    Button(onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date
                            onAction(MovieListAction.OnDateSelected(picked))
                        }
                    }) {
                        Text("Ok")
                    }
                }) {
                DatePicker(
                    state = datePickerState
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        onClick = { onAction(MovieListAction.OnRetry) },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.movies, key = { it.id }) { movie ->
                        MovieCard(
                            movie = movie,
                            onClick = { onAction(MovieListAction.OnMovieClick(movie.id)) }
                        )
                    }
                    if (state.isLoadingMore || state.canLoadMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(color = FlickQColors.Gold)
                                } else {
                                    FlickQButton(
                                        text = "Load more",
                                        onClick = { onAction(MovieListAction.OnLoadMore) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    movie: MovieUi,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        PosterImage(
            url = movie.posterUrl,
            contentDescription = movie.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            text = movie.title,
            color = FlickQColors.TicketPaper,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = listOf(movie.year, movie.duration).filter { it.isNotBlank() }
                .joinToString(" · "),
            color = FlickQColors.SeatAvailable,
            fontSize = 12.sp
        )
    }
}
