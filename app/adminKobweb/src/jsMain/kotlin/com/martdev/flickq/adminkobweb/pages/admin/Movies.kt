package com.martdev.flickq.adminkobweb.pages.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.DateField
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.FIELD_CSS
import com.martdev.flickq.adminkobweb.components.FieldLabel
import com.martdev.flickq.adminkobweb.components.FormCard
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.PosterThumb
import com.martdev.flickq.adminkobweb.components.PrimaryButton
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SaveButton
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.TextAreaField
import com.martdev.flickq.adminkobweb.components.TextField
import com.martdev.flickq.adminkobweb.components.formatDate
import com.martdev.flickq.adminkobweb.components.formatDuration
import com.martdev.flickq.adminkobweb.components.plain
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesAction
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesState
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesViewModel
import com.martdev.flickq.feature.admin.presentation.logic.movies.MovieForm
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
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
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.icons.fa.FaChartColumn
import com.varabyte.kobweb.silk.components.icons.fa.FaClapperboard
import com.varabyte.kobweb.silk.components.icons.fa.FaImage
import com.varabyte.kobweb.silk.components.icons.fa.FaImages
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.icons.fa.FaXmark
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.StyleScope
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input

@Page
@Composable
fun MoviesPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Movies, title = "Movies") {
            MoviesContent()
        }
    }
}

@Composable
private fun MoviesContent() {
    val vm = rememberAdminViewModel<AdminMoviesViewModel>()
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    Box(modifier = Modifier.fillMaxWidth()) {
        val form = state.form
        if (form != null) {
            MovieFormView(state, form, onAction)
        } else {
            MovieListView(state, onAction)
        }
        // Delete-confirm overlay sits above either view.
        state.deleting?.let { movie ->
            DeleteConfirm(movie, onAction)
        }
    }
}

// ---- List ---------------------------------------------------------------------------------

@Composable
private fun MovieListView(state: AdminMoviesState, onAction: (AdminMoviesAction) -> Unit) {
    val error = state.error
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText(
                "Manage catalog, durations, and release schedules.",
                Modifier.color(AdminColors.Body).fontSize(14.px),
            )
            PrimaryButton("Add Movie") { onAction(AdminMoviesAction.OnAddClick) }
        }

        when {
            state.isLoading -> StatusBox("Loading movies…")
            error != null -> ErrorBox(error) { onAction(AdminMoviesAction.OnRetry) }
            state.movies.isEmpty() -> StatusBox("No movies in the catalog yet.")
            else -> MovieTable(state, onAction)
        }
    }
}

@Composable
private fun MovieTable(state: AdminMoviesState, onAction: (AdminMoviesAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px),
    ) {
        // Header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .borderBottom(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Poster", 80.px)
            HeaderCell("Title", null)
            HeaderCell("Genres", 220.px)
            HeaderCell("Duration", 110.px)
            HeaderCell("Release Date", 150.px)
            HeaderCell("Actions", 90.px)
        }
        // Rows.
        state.movies.forEach { movie -> MovieRow(movie, onAction) }
        // Footer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(topBottom = 16.px, leftRight = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpanText("Showing ${state.movies.size} movies", Modifier.color(AdminColors.Body).fontSize(13.px))
            if (state.isLoadingMore) {
                SpanText("Loading…", Modifier.color(AdminColors.Muted).fontSize(13.px))
            } else if (state.canLoadMore) {
                SecondaryButton("Load More") { onAction(AdminMoviesAction.OnLoadMore) }
            }
        }
    }
}

@Composable
private fun MovieRow(movie: Movie, onAction: (AdminMoviesAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .borderBottom(1.px, LineStyle.Solid, AdminColors.Border)
            .padding(topBottom = 12.px, leftRight = 20.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poster.
        Cell(80.px) { PosterThumb(movie.posterUrl, 40, 56) }
        // Title.
        Cell(null) {
            SpanText(movie.title, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
        }
        // Genres.
        Cell(220.px) {
            Div(attrs = { style { wrap() } }) {
                movie.genres.take(3).forEach { GenrePill(it.name) }
            }
        }
        // Duration.
        Cell(110.px) {
            SpanText(formatDuration(movie.duration), Modifier.color(AdminColors.Body).fontSize(14.px))
        }
        // Release date.
        Cell(150.px) { ReleasePill(movie.releasedDate.toString()) }
        // Actions.
        Cell(90.px) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ FaPenToSquare(it) }, AdminColors.Body) { onAction(AdminMoviesAction.OnEditClick(movie)) }
                IconButton({ FaTrash(it) }, AdminColors.Primary) { onAction(AdminMoviesAction.OnDeleteClick(movie)) }
            }
        }
    }
}

// ---- Form ---------------------------------------------------------------------------------

@Composable
private fun MovieFormView(state: AdminMoviesState, form: MovieForm, onAction: (AdminMoviesAction) -> Unit) {
    val editing = form.editingId != null
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(1280.px),
        verticalArrangement = Arrangement.spacedBy(24.px),
    ) {
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                SpanText(
                    if (editing) "Edit Movie" else "Add New Movie",
                    Modifier.montserrat().color(AdminColors.Heading).fontSize(30.px).fontWeight(FontWeight.Bold),
                )
                SpanText(
                    "Configure details, media assets, and metadata for the catalog.",
                    Modifier.color(AdminColors.Body).fontSize(14.px),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
                SpanText(
                    "Cancel",
                    Modifier.color(AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold)
                        .cursor(Cursor.Pointer).padding(leftRight = 12.px)
                        .onClick { onAction(AdminMoviesAction.OnDismissDialog) },
                )
                SaveButton(
                    label = if (state.isSaving) "Saving…" else "Save Movie",
                    enabled = form.isValid && !state.isSaving,
                ) { onAction(AdminMoviesAction.OnSave) }
            }
        }

        state.dialogError?.let {
            SpanText(it.plain(), Modifier.color(AdminColors.Primary).fontSize(13.px))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.px)) {
            // Left column.
            Column(
                modifier = Modifier.flexGrow(2).flexBasis(0.px),
                verticalArrangement = Arrangement.spacedBy(24.px),
            ) {
                FormCard("Core Information", { FaClapperboard(it) }) {
                    FieldLabel("MOVIE TITLE *")
                    TextField(form.title, "Enter the movie title") { onAction(AdminMoviesAction.OnTitleChange(it)) }
                    FieldLabel("SYNOPSIS")
                    TextAreaField(form.description) { onAction(AdminMoviesAction.OnDescriptionChange(it)) }
                }
                FormCard("Metadata & Categorization", { FaChartColumn(it) }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.px)) {
                        Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
                            FieldLabel("DURATION (MINUTES)")
                            TextField(form.duration, "e.g. 120") { onAction(AdminMoviesAction.OnDurationChange(it)) }
                            SpanText("Approx. ${formatDuration(form.duration.toIntOrNull() ?: 0)}", Modifier.color(AdminColors.Muted).fontSize(12.px))
                        }
                        Column(modifier = Modifier.flexGrow(1).flexBasis(0.px), verticalArrangement = Arrangement.spacedBy(8.px)) {
                            FieldLabel("RELEASE DATE")
                            DateField(form.releasedDate) { onAction(AdminMoviesAction.OnReleasedDateChange(it)) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().margin(top = 8.px),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FieldLabel("GENRES")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
                            SpanText("Select multiple", Modifier.color(AdminColors.Muted).fontSize(12.px))
                            AddGenreLink { onAction(AdminMoviesAction.OnAddGenreClick) }
                        }
                    }
                    GenrePicker(state.genres, form.genreIds) { onAction(AdminMoviesAction.OnToggleGenre(it)) }
                    state.newGenre?.let { name ->
                        NewGenreRow(name, state.isSavingGenre, onAction)
                    }
                }
            }
            // Right column.
            Column(modifier = Modifier.flexGrow(1).flexBasis(0.px)) {
                FormCard("Media Assets", { FaImages(it) }) {
                    FieldLabel("POSTER IMAGE URL")
                    TextField(form.posterUrl, "https://…") { onAction(AdminMoviesAction.OnPosterUrlChange(it)) }
                    FieldLabel("PREVIEW")
                    PosterPreview(form.posterUrl)
                }
            }
        }
    }
}

@Composable
private fun GenrePicker(all: List<Genre>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.Surface)
            .border(1.px, LineStyle.Solid, AdminColors.Border)
            .borderRadius(8.px)
            .padding(12.px),
    ) {
        Div(attrs = { style { wrap() } }) {
            all.filter { it.id in selected }.forEach { g ->
                GenreToggle(g.name, isSelected = true) { onToggle(g.id) }
            }
            all.filter { it.id !in selected }.forEach { g ->
                GenreToggle(g.name, isSelected = false) { onToggle(g.id) }
            }
            if (all.isEmpty()) {
                SpanText("No genres available.", Modifier.color(AdminColors.Muted).fontSize(13.px))
            }
        }
    }
}

@Composable
private fun AddGenreLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier.cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.px),
    ) {
        FaPlus(Modifier.color(AdminColors.Primary).fontSize(10.px))
        SpanText("Add Genre", Modifier.color(AdminColors.Primary).fontSize(12.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun NewGenreRow(value: String, saving: Boolean, onAction: (AdminMoviesAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().margin(top = 8.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        Box(modifier = Modifier.flexGrow(1)) {
            Input(type = InputType.Text) {
                value(value)
                attr("placeholder", "New genre name")
                attr("style", FIELD_CSS)
                onInput { onAction(AdminMoviesAction.OnNewGenreChange(it.value)) }
            }
        }
        SaveButton(
            label = if (saving) "Adding…" else "Add",
            enabled = value.isNotBlank() && !saving,
        ) { onAction(AdminMoviesAction.OnSubmitGenre) }
        SpanText(
            "Cancel",
            Modifier.color(AdminColors.Body).fontSize(14.px).fontWeight(FontWeight.SemiBold)
                .cursor(Cursor.Pointer).padding(leftRight = 8.px)
                .onClick { onAction(AdminMoviesAction.OnCancelGenre) },
        )
    }
}

@Composable
private fun GenreToggle(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .backgroundColor(if (isSelected) AdminColors.PrimaryWash else AdminColors.Chip)
            .border(1.px, LineStyle.Solid, if (isSelected) AdminColors.Primary else AdminColors.Border)
            .borderRadius(9999.px)
            .padding(topBottom = 5.px, leftRight = 12.px)
            .cursor(Cursor.Pointer)
            .onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        if (!isSelected) FaPlus(Modifier.color(AdminColors.Body).fontSize(10.px))
        SpanText(name, Modifier.color(if (isSelected) AdminColors.Primary else AdminColors.Body).fontSize(13.px))
        if (isSelected) FaXmark(Modifier.color(AdminColors.Primary).fontSize(11.px))
    }
}

@Composable
private fun PosterPreview(url: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.px)
            .backgroundColor(AdminColors.Surface)
            .border(1.px, LineStyle.Solid, AdminColors.Border)
            .borderRadius(8.px),
        contentAlignment = Alignment.Center,
    ) {
        if (url.startsWith("http")) {
            Img(src = url, attrs = {
                attr("style", "width:100%;height:100%;object-fit:cover;border-radius:8px;")
            })
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.px)) {
                FaImage(Modifier.color(AdminColors.Muted).fontSize(36.px))
                SpanText(
                    "Enter a valid URL above to preview poster",
                    Modifier.color(AdminColors.Muted).fontSize(13.px),
                )
            }
        }
    }
}

// ---- Delete confirm -----------------------------------------------------------------------

@Composable
private fun DeleteConfirm(movie: Movie, onAction: (AdminMoviesAction) -> Unit) {
    Box(
        modifier = Modifier
            .styleModifier {
                property("position", "fixed")
                property("inset", "0")
                property("background", "rgba(1,15,31,0.7)")
                property("z-index", "50")
            }
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .maxWidth(420.px)
                .fillMaxWidth()
                .backgroundColor(AdminColors.SurfaceAlt)
                .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                .borderRadius(12.px)
                .padding(24.px),
            verticalArrangement = Arrangement.spacedBy(16.px),
        ) {
            SpanText("Delete movie", Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px).fontWeight(FontWeight.Bold))
            SpanText(
                "Delete \"${movie.title}\"? This can't be undone.",
                Modifier.color(AdminColors.Body).fontSize(14.px),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.px)) {
                Box(modifier = Modifier.flexGrow(1)) { SecondaryButton("Cancel") { onAction(AdminMoviesAction.OnDismissDelete) } }
                Box(modifier = Modifier.flexGrow(1)) { PrimaryButton("Delete") { onAction(AdminMoviesAction.OnConfirmDelete) } }
            }
        }
    }
}

// ---- Shared bits --------------------------------------------------------------------------

@Composable
private fun GenrePill(name: String) {
    Box(
        modifier = Modifier
            .backgroundColor(AdminColors.Chip)
            .borderRadius(9999.px)
            .padding(topBottom = 3.px, leftRight = 9.px),
    ) {
        SpanText(name, Modifier.color(AdminColors.Body).fontSize(12.px))
    }
}

@Composable
private fun ReleasePill(iso: String) {
    Row(
        modifier = Modifier
            .backgroundColor(AdminColors.Chip)
            .borderRadius(9999.px)
            .padding(topBottom = 4.px, leftRight = 10.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        Box(Modifier.size(7.px).backgroundColor(AdminColors.Success).borderRadius(9999.px))
        SpanText(formatDate(iso), Modifier.color(AdminColors.Body).fontSize(12.px))
    }
}

// ---- helpers ------------------------------------------------------------------------------

/** flex-wrap row for chips, applied to a raw <div>. */
private fun StyleScope.wrap() {
    property("display", "flex")
    property("flex-wrap", "wrap")
    property("gap", "8px")
    property("align-items", "center")
}

