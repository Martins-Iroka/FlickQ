package com.martdev.flickq.adminkobweb.pages.admin.showtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.DateTimeField
import com.martdev.flickq.adminkobweb.components.Dropdown
import com.martdev.flickq.adminkobweb.components.FIELD_CSS
import com.martdev.flickq.adminkobweb.components.FieldLabel
import com.martdev.flickq.adminkobweb.components.FormCard
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.PosterThumb
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SaveButton
import com.martdev.flickq.adminkobweb.components.SecondaryButtonInline
import com.martdev.flickq.adminkobweb.components.formatDuration
import com.martdev.flickq.adminkobweb.components.fromLocalInput
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AddEditShowtimeAction
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AddEditShowtimeEvent
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AddEditShowtimeState
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminAddEditShowtimeViewModel
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.ShowtimeData
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.TheShowtimeForm
import com.martdev.flickq.movie.model.Movie
import com.varabyte.kobweb.browser.storage.getItem
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
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.icons.fa.FaXmark
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Input
import org.koin.core.parameter.parametersOf

@Page("info")
@Composable
fun ShowtimePage() {
    RequireAdmin {
        AdminLayout(AdminNav.Showtimes, title = "Showtime") {
            ShowtimeContent()
        }
    }
}

@Composable
private fun ShowtimeContent() {
    val ctx = rememberPageContext()
    val mode = ctx.route.params["mode"].orEmpty()
    val showtimeFormData = if (mode == "edit") {
        val showtimeStorageKey = ShowtimeStorageKey("showtime")
        val showtimeData = sessionStorage.getItem(showtimeStorageKey) ?: ShowtimeData()
        TheShowtimeForm(
            editingId = showtimeData.editingId,
            movieId = showtimeData.movieId,
            roomId = showtimeData.roomId,
            startsAt = showtimeData.startsAt,
            endsAt = showtimeData.endsAt,
            price = showtimeData.price,
            status = showtimeData.status
        )
    } else TheShowtimeForm()
    val vm = rememberAdminViewModel<AdminAddEditShowtimeViewModel> {
        parametersOf(showtimeFormData)
    }
    val state by vm.state.collectAsState()
    val onAction = vm::onAction
    ObserveAsEvents(vm.event) { event ->
        when (event) {
            AddEditShowtimeEvent.NavigateToList -> ctx.router.navigateTo("/admin/showtime/list")
        }
    }
    ShowtimeFormView(state, state.form, onAction)
}


@Composable
private fun ShowtimeFormView(
    state: AddEditShowtimeState,
    form: TheShowtimeForm,
    onAction: (AddEditShowtimeAction) -> Unit
) {
    val editing = form.editingId != 0L
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        SpanText(
            if (editing) "Showtimes ›  Edit" else "Showtimes ›  Schedule New",
            Modifier.color(AdminColors.Body).fontSize(13.px).fontWeight(FontWeight.SemiBold),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText(
                    if (editing) "Edit Showtime" else "Schedule Showtime",
                    Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px)
                        .fontWeight(FontWeight.Bold)
                )
                SpanText(
                    "Allocate movies to rooms and define screening periods.",
                    Modifier.color(AdminColors.Body).fontSize(14.px)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.px)
            ) {
                SecondaryButtonInline("Cancel") { }
                SaveButton(
                    label = if (state.isSaving) "Saving…" else "Save Showtime",
                    enabled = form.isValid && !state.isSaving,
                ) { onAction(AddEditShowtimeAction.OnSave) }
            }
        }

        state.dialogError?.let {
            SpanText(
                it.plain(),
                Modifier.color(AdminColors.Primary).fontSize(13.px)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.px)
        ) {
            // Left: event details + schedule.
            Column(
                modifier = Modifier.flexGrow(2).flexBasis(0.px).minWidth(380.px),
                verticalArrangement = Arrangement.spacedBy(24.px)
            ) {
                FormCard("Event Details", { FaTicket(it) }) {
                    FieldLabel("MOVIE SELECTION *")
                    MoviePicker(state, form, onAction)
                    FieldLabel("THEATER ROOM *")
                    Dropdown(
                        value = form.roomId,
                        placeholder = "Select a room…",
                        options = state.rooms.map { it.id.toString() to "Room ${it.name} (${it.rows * it.columns} seats)" },
                        onSelect = { onAction(AddEditShowtimeAction.OnRoomIdChange(it)) },
                    )
                }
                FormCard("Schedule", { FaClock(it) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.px)
                    ) {
                        Column(
                            modifier = Modifier.flexGrow(1).flexBasis(0.px),
                            verticalArrangement = Arrangement.spacedBy(8.px)
                        ) {
                            FieldLabel("START DATE & TIME")
                            DateTimeField(form.startsAt) {
                                onAction(
                                    AddEditShowtimeAction.OnStartsAtChange(
                                        fromLocalInput(it)
                                    )
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.flexGrow(1).flexBasis(0.px),
                            verticalArrangement = Arrangement.spacedBy(8.px)
                        ) {
                            FieldLabel("END DATE & TIME")
                            DateTimeField(form.endsAt) {
                                onAction(
                                    AddEditShowtimeAction.OnEndsAtChange(
                                        fromLocalInput(it)
                                    )
                                )
                            }
                            val movie = state.selectedMovie
                            if (movie != null && movie.duration > 0 && !form.endEdited) {
                                SpanText(
                                    "ⓘ Auto-suggested: ${formatDuration(movie.duration + 15)} (includes 15m buffer)",
                                    Modifier.color(AdminColors.Amber).fontSize(12.px),
                                )
                            }
                        }
                    }
                }
            }
            // Right: ticketing.
            Column(modifier = Modifier.flexGrow(1).flexBasis(0.px).minWidth(260.px)) {
                FormCard("Ticketing", { FaTicket(it) }) {
                    FieldLabel("BASE TICKET PRICE")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.px),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpanText(
                            "₦",
                            Modifier.color(AdminColors.Body).fontSize(16.px)
                                .fontWeight(FontWeight.SemiBold)
                        )
                        Box(modifier = Modifier.flexGrow(1)) {
                            Input(type = InputType.Text) {
                                value(form.price)
                                attr("placeholder", "8500")
                                attr("style", FIELD_CSS)
                                onInput { onAction(AddEditShowtimeAction.OnPriceChange(it.value)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePicker(
    state: AddEditShowtimeState,
    form: TheShowtimeForm,
    onAction: (AddEditShowtimeAction) -> Unit
) {
    val selectedId = form.movieId.toLongOrNull()
    if (selectedId != null) {
        val movie = state.selectedMovie?.takeIf { it.id == selectedId } ?: state.movie(selectedId)
        Row(
            modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Surface)
                .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px)
                .padding(12.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            PosterThumb(movie?.posterUrl ?: "", 44, 60)
            Column(
                modifier = Modifier.flexGrow(1),
                verticalArrangement = Arrangement.spacedBy(3.px)
            ) {
                SpanText(
                    movie?.title ?: "Movie $selectedId",
                    Modifier.color(AdminColors.Heading).fontSize(15.px)
                        .fontWeight(FontWeight.SemiBold)
                )
                val runtime = movie?.duration
                SpanText(
                    if (runtime != null && runtime > 0) "Runtime: ${formatDuration(runtime)}" else "Loading runtime…",
                    Modifier.color(AdminColors.Muted).fontSize(12.px),
                )
            }
            IconButton(
                { FaXmark(it) },
                AdminColors.Body
            ) { onAction(AddEditShowtimeAction.OnClearMovie) }
        }
    } else {
        MovieSearch(state.movies) { onAction(AddEditShowtimeAction.OnMoviePicked(it)) }
    }
}

@Composable
private fun MovieSearch(movies: List<Movie>, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.px)) {
        Input(type = InputType.Text) {
            value(query)
            attr("placeholder", "Search movies…")
            attr("style", FIELD_CSS)
            onInput { query = it.value }
        }
        val q = query.trim().lowercase()
        if (q.isNotBlank()) {
            val results = movies.filter { q in it.title.lowercase() }.take(8)
            Column(
                modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Surface)
                    .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px),
            ) {
                if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.px)) {
                        SpanText(
                            "No movies match “$query”.",
                            Modifier.color(AdminColors.Muted).fontSize(13.px)
                        )
                    }
                } else {
                    results.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(topBottom = 8.px, leftRight = 12.px)
                                .cursor(Cursor.Pointer).onClick { onPick(m.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.px),
                        ) {
                            PosterThumb(m.posterUrl, 30, 42)
                            SpanText(m.title, Modifier.color(AdminColors.Body).fontSize(14.px))
                        }
                    }
                }
            }
        }
    }
}