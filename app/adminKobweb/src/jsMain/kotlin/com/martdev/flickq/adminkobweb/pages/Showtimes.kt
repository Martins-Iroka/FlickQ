package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesAction
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesState
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesViewModel
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.ShowtimeForm
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextDecorationLine
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderBottom
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.textDecorationLine
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.icons.fa.FaCheck
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.icons.fa.FaTicket
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.icons.fa.FaXmark
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.core.Page
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input

@Page
@Composable
fun ShowtimesPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Showtimes, title = "Showtimes") {
            ShowtimesContent()
        }
    }
}

@Composable
private fun ShowtimesContent() {
    val vm = rememberAdminViewModel<AdminShowtimesViewModel>()
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    Box(modifier = Modifier.fillMaxWidth()) {
        val form = state.form
        if (form != null) ShowtimeFormView(state, form, onAction) else ShowtimeListView(state, onAction)

        state.deleting?.let { st -> DeleteConfirm(st, onAction) }
        state.populatingFor?.let { st -> PopulateConfirm(st, onAction) }
        state.statusFor?.let { st -> StatusPicker(st, onAction) }
    }
}

// ---- List ---------------------------------------------------------------------------------

@Composable
private fun ShowtimeListView(state: AdminShowtimesState, onAction: (AdminShowtimesAction) -> Unit) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    val pageSize = 10

    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText("Showtime Management", Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold))
                SpanText("Manage schedules, room assignments, and pricing.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
            PrimaryButton("Create Showtime") { onAction(AdminShowtimesAction.OnAddClick) }
        }

        state.message?.let { msg ->
            Box(
                modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SuccessWash)
                    .border(1.px, LineStyle.Solid, AdminColors.Success).borderRadius(8.px)
                    .padding(topBottom = 10.px, leftRight = 14.px),
            ) { SpanText(msg.plain(), Modifier.color(AdminColors.Success).fontSize(13.px)) }
        }

        val error = state.error
        when {
            state.isLoading -> StatusBox("Loading showtimes…")
            error != null -> ErrorBox(error) { onAction(AdminShowtimesAction.OnRetry) }
            else -> {
                // Client-side filter over the loaded pages.
                val filtered = state.showtimes.filter { st ->
                    val title = state.movie(st.movieId)?.title ?: ""
                    val room = state.room(st.roomId)?.name ?: ""
                    val q = query.trim().lowercase()
                    (q.isBlank() || q in title.lowercase() || q in room.lowercase()) &&
                        (statusFilter.isBlank() || st.status.name == statusFilter) &&
                        (dateFilter.isBlank() || isoOf(st.startsAt).take(10) == dateFilter)
                }
                ListCard(state, filtered, page, pageSize, query, statusFilter, dateFilter,
                    onQuery = { query = it; page = 0 },
                    onStatus = { statusFilter = it; page = 0 },
                    onDate = { dateFilter = it; page = 0 },
                    onPage = { page = it },
                    onAction = onAction)
            }
        }
    }
}

@Composable
private fun ListCard(
    state: AdminShowtimesState,
    filtered: List<Showtime>,
    page: Int,
    pageSize: Int,
    query: String,
    statusFilter: String,
    dateFilter: String,
    onQuery: (String) -> Unit,
    onStatus: (String) -> Unit,
    onDate: (String) -> Unit,
    onPage: (Int) -> Unit,
    onAction: (AdminShowtimesAction) -> Unit,
) {
    val total = filtered.size
    val lastPage = if (total == 0) 0 else (total - 1) / pageSize
    val safePage = page.coerceIn(0, lastPage)
    val start = safePage * pageSize
    val visible = filtered.drop(start).take(pageSize)

    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px),
    ) {
        // Filters.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(16.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            Box(modifier = Modifier.flexGrow(1)) {
                Input(type = InputType.Text) {
                    value(query)
                    attr("placeholder", "Search by movie or room…")
                    attr("style", FIELD_CSS)
                    onInput { onQuery(it.value) }
                }
            }
            Box(modifier = Modifier.width(180.px)) {
                Dropdown(
                    value = statusFilter,
                    placeholder = "All Statuses",
                    options = listOf("" to "All Statuses") + ShowtimeStatus.entries.map { it.name to it.name.titlecaseWord() },
                    onSelect = onStatus,
                )
            }
            Box(modifier = Modifier.width(170.px)) {
                Input(type = InputType.Date) {
                    value(dateFilter)
                    attr("style", FIELD_CSS)
                    onInput { onDate(it.value) }
                }
            }
        }
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
                .padding(topBottom = 12.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Movie", null)
            HeaderCell("Room", 150.px)
            HeaderCell("Start", 150.px)
            HeaderCell("End", 90.px)
            HeaderCell("Price", 110.px)
            HeaderCell("Status", 130.px)
            HeaderCell("Actions", 140.px)
        }
        when {
            total == 0 -> StatusBox("No showtimes match your filters.")
            else -> visible.forEach { st -> ShowtimeRow(state, st, onAction) }
        }
        // Footer / pagination.
        Row(
            modifier = Modifier.fillMaxWidth().padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val from = if (total == 0) 0 else start + 1
            val to = start + visible.size
            SpanText("Showing $from to $to of $total", Modifier.color(AdminColors.Body).fontSize(13.px))
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                if (state.canLoadMore) {
                    PagerButton("Load more", enabled = true) { onAction(AdminShowtimesAction.OnLoadMore) }
                }
                PagerButton("Prev", enabled = safePage > 0) { onPage(safePage - 1) }
                SpanText("${safePage + 1} / ${lastPage + 1}", Modifier.color(AdminColors.Body).fontSize(13.px))
                PagerButton("Next", enabled = safePage < lastPage) { onPage(safePage + 1) }
            }
        }
    }
}

@Composable
private fun ShowtimeRow(state: AdminShowtimesState, st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    val cancelled = st.status == ShowtimeStatus.CANCELLED
    val movie = state.movie(st.movieId)
    val roomName = state.room(st.roomId)?.name ?: "Room ${st.roomId}"
    Row(
        modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px)
            .thenIf(cancelled) { Modifier.opacity(0.55) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                PosterThumb(movie?.posterUrl ?: "", 36, 50)
                SpanText(movie?.title ?: "Movie ${st.movieId}", Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
            }
        }
        Cell(150.px) {
            SpanText(
                roomName,
                Modifier.color(AdminColors.Body).fontSize(14.px)
                    .thenIf(cancelled) { Modifier.textDecorationLine(TextDecorationLine.LineThrough) },
            )
        }
        Cell(150.px) {
            Column(verticalArrangement = Arrangement.spacedBy(2.px)) {
                SpanText(dispDate(st.startsAt), Modifier.color(AdminColors.Body).fontSize(13.px))
                SpanText(dispTime(st.startsAt), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
            }
        }
        Cell(90.px) { SpanText(dispTime(st.endsAt), Modifier.color(AdminColors.Body).fontSize(14.px)) }
        Cell(110.px) { SpanText(formatNaira(st.price), Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
        Cell(130.px) { StatusBadge(st.status) }
        Cell(140.px) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ FaPenToSquare(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnEditClick(st)) }
                IconButton({ FaClock(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnStatusClick(st)) }
                IconButton({ FaTicket(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnPopulateClick(st)) }
                IconButton({ FaTrash(it) }, AdminColors.Primary) { onAction(AdminShowtimesAction.OnDeleteClick(st)) }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ShowtimeStatus) {
    val (bg, fg) = when (status) {
        ShowtimeStatus.SCHEDULED -> AdminColors.SuccessChip to AdminColors.Success
        ShowtimeStatus.COMPLETED -> AdminColors.Chip to AdminColors.Muted
        ShowtimeStatus.CANCELLED -> AdminColors.PrimaryWash to AdminColors.Primary
    }
    Row(
        modifier = Modifier.backgroundColor(bg).borderRadius(9999.px).padding(topBottom = 4.px, leftRight = 10.px),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        Box(Modifier.size(7.px).backgroundColor(fg).borderRadius(9999.px))
        SpanText(status.name, Modifier.color(fg).fontSize(11.px).fontWeight(FontWeight.SemiBold))
    }
}

// ---- Form ---------------------------------------------------------------------------------

@Composable
private fun ShowtimeFormView(state: AdminShowtimesState, form: ShowtimeForm, onAction: (AdminShowtimesAction) -> Unit) {
    val editing = form.editingId != null
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
                SpanText(if (editing) "Edit Showtime" else "Schedule Showtime", Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold))
                SpanText("Allocate movies to rooms and define screening periods.", Modifier.color(AdminColors.Body).fontSize(14.px))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                SecondaryButtonInline("Cancel") { onAction(AdminShowtimesAction.OnDismissDialog) }
                SaveButton(
                    label = if (state.isSaving) "Saving…" else "Save Showtime",
                    enabled = form.isValid && !state.isSaving,
                ) { onAction(AdminShowtimesAction.OnSave) }
            }
        }

        state.dialogError?.let { SpanText(it.plain(), Modifier.color(AdminColors.Primary).fontSize(13.px)) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
            // Left: event details + schedule.
            Column(modifier = Modifier.flexGrow(2).flexBasis(0.px).minWidth(380.px), verticalArrangement = Arrangement.spacedBy(24.px)) {
                FormCard("Event Details", { FaTicket(it) }) {
                    FieldLabel("MOVIE SELECTION *")
                    MoviePicker(state, form, onAction)
                    FieldLabel("THEATER ROOM *")
                    Dropdown(
                        value = form.roomId,
                        placeholder = "Select a room…",
                        options = state.rooms.map { it.id.toString() to "Room ${it.name} (${it.rows * it.columns} seats)" },
                        onSelect = { onAction(AdminShowtimesAction.OnRoomIdChange(it)) },
                    )
                }
                FormCard("Schedule", { FaClock(it) }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.px)) {
                        Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
                            FieldLabel("START DATE & TIME")
                            DateTimeField(form.startsAt) { onAction(AdminShowtimesAction.OnStartsAtChange(fromLocalInput(it))) }
                        }
                        Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
                            FieldLabel("END DATE & TIME")
                            DateTimeField(form.endsAt) { onAction(AdminShowtimesAction.OnEndsAtChange(fromLocalInput(it))) }
                            val movie = state.selectedMovie
                            if (movie != null && movie.duration > 0 && !form.endEdited) {
                                SpanText(
                                    "ⓘ Auto-suggested: ${formatRuntime(movie.duration + 15)} (includes 15m buffer)",
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px), modifier = Modifier.fillMaxWidth()) {
                        SpanText("₦", Modifier.color(AdminColors.Body).fontSize(16.px).fontWeight(FontWeight.SemiBold))
                        Box(modifier = Modifier.flexGrow(1)) {
                            Input(type = InputType.Text) {
                                value(form.price)
                                attr("placeholder", "8500")
                                attr("style", FIELD_CSS)
                                onInput { onAction(AdminShowtimesAction.OnPriceChange(it.value)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePicker(state: AdminShowtimesState, form: ShowtimeForm, onAction: (AdminShowtimesAction) -> Unit) {
    val selectedId = form.movieId.toLongOrNull()
    if (selectedId != null) {
        val movie = state.selectedMovie?.takeIf { it.id == selectedId } ?: state.movie(selectedId)
        Row(
            modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Surface)
                .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px).padding(12.px),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            PosterThumb(movie?.posterUrl ?: "", 44, 60)
            Column(modifier = Modifier.flexGrow(1), verticalArrangement = Arrangement.spacedBy(3.px)) {
                SpanText(movie?.title ?: "Movie $selectedId", Modifier.color(AdminColors.Heading).fontSize(15.px).fontWeight(FontWeight.SemiBold))
                val runtime = state.selectedMovie?.takeIf { it.id == selectedId }?.duration
                SpanText(
                    if (runtime != null && runtime > 0) "Runtime: ${formatRuntime(runtime)}" else "Loading runtime…",
                    Modifier.color(AdminColors.Muted).fontSize(12.px),
                )
            }
            IconButton({ FaXmark(it) }, AdminColors.Body) { onAction(AdminShowtimesAction.OnClearMovie) }
        }
    } else {
        MovieSearch(state.movies) { onAction(AdminShowtimesAction.OnMoviePicked(it)) }
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
                        SpanText("No movies match “$query”.", Modifier.color(AdminColors.Muted).fontSize(13.px))
                    }
                } else {
                    results.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(topBottom = 8.px, leftRight = 12.px)
                                .cursor(Cursor.Pointer).onClick { onPick(m.id) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px),
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

// ---- Overlays -----------------------------------------------------------------------------

@Composable
private fun DeleteConfirm(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Delete showtime", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText("Showtime ${st.id} will be removed. Showtimes with reservations can't be deleted.", Modifier.color(AdminColors.Body).fontSize(14.px))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminShowtimesAction.OnDismissDelete) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButton("Delete") { onAction(AdminShowtimesAction.OnConfirmDelete) } }
        }
    }
}

@Composable
private fun PopulateConfirm(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Populate seats", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        SpanText("Seed the showtime-seat grid for showtime ${st.id} from its room layout.", Modifier.color(AdminColors.Body).fontSize(14.px))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
            Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminShowtimesAction.OnDismissPopulate) } }
            Box(modifier = Modifier.flexGrow(1)) { PrimaryButtonPlain("Populate") { onAction(AdminShowtimesAction.OnConfirmPopulate) } }
        }
    }
}

@Composable
private fun StatusPicker(st: Showtime, onAction: (AdminShowtimesAction) -> Unit) {
    Overlay {
        SpanText("Set status", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.px)) {
            ShowtimeStatus.entries.forEach { status ->
                val selected = status == st.status
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .backgroundColor(if (selected) AdminColors.PrimaryWash else AdminColors.Surface)
                        .border(1.px, LineStyle.Solid, if (selected) AdminColors.Primary else AdminColors.Border)
                        .borderRadius(8.px).padding(topBottom = 12.px, leftRight = 14.px)
                        .cursor(Cursor.Pointer).onClick { onAction(AdminShowtimesAction.OnStatusPicked(status)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpanText(status.name, Modifier.color(if (selected) AdminColors.Primary else AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold))
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) { SecondaryButton("Close") { onAction(AdminShowtimesAction.OnDismissStatus) } }
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.styleModifier {
            property("position", "fixed"); property("inset", "0")
            property("background", "rgba(1,15,31,0.7)"); property("z-index", "50")
        }.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.maxWidth(440.px).fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
                .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(24.px),
            verticalArrangement = Arrangement.spacedBy(16.px),
        ) { content() }
    }
}

// ---- Custom dropdown ----------------------------------------------------------------------

@Composable
private fun Dropdown(value: String, placeholder: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: placeholder
    Box(modifier = Modifier.fillMaxWidth().styleModifier { property("position", "relative") }) {
        Row(
            modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Surface)
                .border(1.px, LineStyle.Solid, AdminColors.Border).borderRadius(8.px)
                .padding(topBottom = 11.px, leftRight = 13.px).cursor(Cursor.Pointer).onClick { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText(label, Modifier.color(if (value.isBlank()) AdminColors.Muted else AdminColors.Body).fontSize(14.px))
            SpanText("▾", Modifier.color(AdminColors.Muted).fontSize(12.px))
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
                    .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
                    .styleModifier {
                        property("position", "absolute"); property("top", "calc(100% + 4px)")
                        property("left", "0"); property("right", "0"); property("z-index", "40")
                        property("max-height", "240px"); property("overflow-y", "auto")
                    },
            ) {
                if (options.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.px)) {
                        SpanText("No options.", Modifier.color(AdminColors.Muted).fontSize(13.px))
                    }
                }
                options.forEach { (v, l) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(topBottom = 10.px, leftRight = 13.px)
                            .cursor(Cursor.Pointer).onClick { onSelect(v); open = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpanText(l, Modifier.color(if (v == value) AdminColors.Primary else AdminColors.Body).fontSize(14.px))
                    }
                }
            }
        }
    }
}

// ---- Shared bits --------------------------------------------------------------------------

private typealias StCellWidth = org.jetbrains.compose.web.css.CSSNumericValue<out org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage>

@Composable
private fun HeaderCell(text: String, width: StCellWidth?) {
    Cell(width) { SpanText(text, Modifier.color(AdminColors.Muted).fontSize(12.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
private fun Cell(width: StCellWidth?, content: @Composable () -> Unit) {
    val mod = if (width != null) Modifier.width(width) else Modifier.flexGrow(1).flexBasis(0.px)
    Box(modifier = mod) { content() }
}

@Composable
private fun PosterThumb(url: String, w: Int, h: Int) {
    if (url.startsWith("http")) {
        Img(src = url, attrs = { attr("style", "width:${w}px;height:${h}px;object-fit:cover;border-radius:4px;") })
    } else {
        Box(modifier = Modifier.width(w.px).height(h.px).backgroundColor(AdminColors.Chip).borderRadius(4.px))
    }
}

@Composable
private fun IconButton(icon: @Composable (Modifier) -> Unit, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.px).backgroundColor(AdminColors.Chip).borderRadius(8.px)
            .cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { icon(Modifier.color(tint).fontSize(13.px)) }
}

@Composable
private fun FormCard(title: String, icon: @Composable (Modifier) -> Unit, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(24.px),
        verticalArrangement = Arrangement.spacedBy(14.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 14.px),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px),
        ) {
            icon(Modifier.color(AdminColors.Primary).fontSize(16.px))
            SpanText(title, Modifier.montserrat().color(AdminColors.Heading).fontSize(18.px).fontWeight(FontWeight.SemiBold))
        }
        body()
    }
}

@Composable
private fun FieldLabel(text: String) {
    SpanText(text, Modifier.color(AdminColors.Muted).fontSize(11.px).fontWeight(FontWeight.SemiBold))
}

@Composable
private fun DateTimeField(iso: String, onValue: (String) -> Unit) {
    Input(type = InputType.DateTimeLocal) {
        value(toLocalInput(iso))
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaPlus(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun PrimaryButtonPlain(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
private fun SaveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .thenIf(!enabled) { Modifier.opacity(0.5) }.cursor(Cursor.Pointer)
            .thenIf(enabled) { Modifier.onClick { onClick() } },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaCheck(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
private fun SecondaryButtonInline(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(8.px)
            .padding(topBottom = 11.px, leftRight = 18.px).cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)) }
}

@Composable
private fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.backgroundColor(AdminColors.Chip).border(1.px, LineStyle.Solid, AdminColors.Border)
            .borderRadius(6.px).padding(topBottom = 6.px, leftRight = 12.px)
            .thenIf(!enabled) { Modifier.opacity(0.4) }
            .thenIf(enabled) { Modifier.cursor(Cursor.Pointer).onClick { onClick() } },
        contentAlignment = Alignment.Center,
    ) { SpanText(label, Modifier.color(AdminColors.Heading).fontSize(13.px)) }
}

@Composable
private fun StatusBox(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(40.px), contentAlignment = Alignment.Center) {
        SpanText(message, Modifier.color(AdminColors.Muted).fontSize(15.px))
    }
}

@Composable
private fun ErrorBox(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(32.px),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.px),
    ) {
        SpanText(error.plain(), Modifier.color(AdminColors.Body).fontSize(14.px))
        Box(modifier = Modifier.width(140.px)) { SecondaryButton("Retry", onRetry) }
    }
}

// ---- helpers ------------------------------------------------------------------------------

private fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

/** Instant (UTC wall-clock) → "yyyy-MM-ddTHH:mm" for a datetime-local input. */
private fun toLocalInput(iso: String): String = if (iso.length >= 16) iso.substring(0, 16) else ""

/** datetime-local value → ISO instant string with a UTC offset the VM can parse. */
private fun fromLocalInput(local: String): String = when {
    local.isBlank() -> ""
    local.length == 16 -> "$local:00Z"
    else -> "${local}Z"
}

private fun isoOf(instant: kotlin.time.Instant): String = instant.toString()

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Instant → "Nov 15, 2023" using the UTC wall-clock encoded in its ISO string. */
private fun dispDate(instant: kotlin.time.Instant): String {
    val iso = instant.toString()
    val parts = iso.take(10).split("-")
    if (parts.size != 3) return iso.take(10)
    val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return iso.take(10)
    return "$month ${parts[2]}, ${parts[0]}"
}

/** Instant → "19:00". */
private fun dispTime(instant: kotlin.time.Instant): String {
    val iso = instant.toString()
    return if (iso.length >= 16) iso.substring(11, 16) else ""
}

private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}

private fun formatNaira(amount: Int): String {
    val grouped = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₦$grouped"
}

private fun String.titlecaseWord(): String =
    lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private const val FIELD_CSS =
    "width:100%;box-sizing:border-box;background-color:#16273a;border:1px solid #30435a;" +
        "border-radius:8px;padding:11px 13px;color:#e9bcb6;font-family:Inter,system-ui,sans-serif;" +
        "font-size:14px;outline:none;"
