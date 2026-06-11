package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.UiText
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
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.flexBasis
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.components.icons.fa.FaCheck
import com.varabyte.kobweb.silk.components.icons.fa.FaChartColumn
import com.varabyte.kobweb.silk.components.icons.fa.FaClapperboard
import com.varabyte.kobweb.silk.components.icons.fa.FaClock
import com.varabyte.kobweb.silk.components.icons.fa.FaImage
import com.varabyte.kobweb.silk.components.icons.fa.FaImages
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaPlus
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.icons.fa.FaXmark
import com.varabyte.kobweb.core.Page
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea

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
                        SpanText("Select multiple", Modifier.color(AdminColors.Muted).fontSize(12.px))
                    }
                    GenrePicker(state.genres, form.genreIds) { onAction(AdminMoviesAction.OnToggleGenre(it)) }
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

private typealias CellWidth = org.jetbrains.compose.web.css.CSSNumericValue<out org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage>

@Composable
private fun HeaderCell(text: String, width: CellWidth?) {
    Cell(width) {
        SpanText(text, Modifier.color(AdminColors.Muted).fontSize(12.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun Cell(width: CellWidth?, content: @Composable () -> Unit) {
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

@Composable
private fun IconButton(icon: @Composable (Modifier) -> Unit, tint: com.varabyte.kobweb.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.px)
            .backgroundColor(AdminColors.Chip)
            .borderRadius(8.px)
            .cursor(Cursor.Pointer)
            .onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        icon(Modifier.color(tint).fontSize(13.px))
    }
}

@Composable
private fun FormCard(title: String, icon: @Composable (Modifier) -> Unit, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(12.px)
            .padding(24.px),
        verticalArrangement = Arrangement.spacedBy(14.px),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().borderBottom(1.px, LineStyle.Solid, AdminColors.Border).padding(bottom = 14.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.px),
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
private fun TextField(value: String, placeholder: String, onValue: (String) -> Unit) {
    Input(type = InputType.Text) {
        value(value)
        attr("placeholder", placeholder)
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

@Composable
private fun TextAreaField(value: String, onValue: (String) -> Unit) {
    TextArea(value = value, attrs = {
        attr("style", FIELD_CSS + "min-height:150px;resize:vertical;")
        onInput { onValue(it.value) }
    })
}

@Composable
private fun DateField(value: String, onValue: (String) -> Unit) {
    Input(type = InputType.Date) {
        value(value)
        attr("style", FIELD_CSS)
        onInput { onValue(it.value) }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .cursor(Cursor.Pointer).onClick { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaPlus(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun SaveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .backgroundColor(AdminColors.Primary).color(AdminColors.OnPrimary)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .thenIf(!enabled) { Modifier.opacity(0.5) }
            .cursor(Cursor.Pointer)
            .thenIf(enabled) { Modifier.onClick { onClick() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        FaCheck(Modifier.color(AdminColors.OnPrimary).fontSize(13.px))
        SpanText(label, Modifier.fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(AdminColors.Chip)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
            .borderRadius(8.px).padding(topBottom = 11.px, leftRight = 18.px)
            .cursor(Cursor.Pointer).onClick { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        SpanText(label, Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold))
    }
}

@Composable
private fun StatusBox(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.px), contentAlignment = Alignment.Center) {
        SpanText(message, Modifier.color(AdminColors.Muted).fontSize(16.px))
    }
}

@Composable
private fun ErrorBox(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth().backgroundColor(AdminColors.SurfaceAlt)
            .border(1.px, LineStyle.Solid, AdminColors.BorderWarm).borderRadius(12.px).padding(32.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.px),
    ) {
        SpanText(error.plain(), Modifier.color(AdminColors.Body).fontSize(14.px))
        Box(modifier = Modifier.width(140.px)) { SecondaryButton("Retry", onRetry) }
    }
}

// ---- helpers ------------------------------------------------------------------------------

/** flex-wrap row for chips, applied to a raw <div>. */
private fun org.jetbrains.compose.web.css.StyleScope.wrap() {
    property("display", "flex")
    property("flex-wrap", "wrap")
    property("gap", "8px")
    property("align-items", "center")
}

private fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2024-03-01" -> "Mar 01, 2024"; passes through anything unexpected. */
private fun formatDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return iso
    return "$month ${parts[2]}, ${parts[0]}"
}

private const val FIELD_CSS =
    "width:100%;box-sizing:border-box;background-color:#16273a;border:1px solid #30435a;" +
        "border-radius:8px;padding:11px 13px;color:#e9bcb6;font-family:Inter,system-ui,sans-serif;" +
        "font-size:14px;outline:none;"
