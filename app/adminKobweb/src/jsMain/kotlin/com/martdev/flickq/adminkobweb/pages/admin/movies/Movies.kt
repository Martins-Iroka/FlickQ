package com.martdev.flickq.adminkobweb.pages.admin.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.Cell
import com.martdev.flickq.adminkobweb.components.ErrorBox
import com.martdev.flickq.adminkobweb.components.HeaderCell
import com.martdev.flickq.adminkobweb.components.IconButton
import com.martdev.flickq.adminkobweb.components.PosterThumb
import com.martdev.flickq.adminkobweb.components.PrimaryButton
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.martdev.flickq.adminkobweb.components.SecondaryButton
import com.martdev.flickq.adminkobweb.components.StatusBox
import com.martdev.flickq.adminkobweb.components.formatDate
import com.martdev.flickq.adminkobweb.components.formatDuration
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesAction
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesEvent
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesState
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesViewModel
import com.martdev.flickq.movie.model.Movie
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
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.icons.fa.FaPenToSquare
import com.varabyte.kobweb.silk.components.icons.fa.FaTrash
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.StyleScope
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div

@Page("items")
@Composable
fun MoviesPage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Movies, title = "Movie") {
            MoviesContent()
        }
    }
}

@Composable
private fun MoviesContent() {
    val vm = rememberAdminViewModel<AdminMoviesViewModel>()
    val ctx = rememberPageContext()
    val state by vm.state.collectAsState()
    val onAction = vm::onAction

    ObserveAsEvents(vm.events) { event ->
        when (event) {
            AdminMoviesEvent.AddNewMovie -> ctx.router.navigateTo("/admin/movies/item?mode=add")
            is AdminMoviesEvent.EditMovie -> ctx.router.navigateTo("/admin/movies/item?id=${event.id}&mode=edit")
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        MovieListView(state, onAction)
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
            SpanText(
                "Showing ${state.movies.size} movies",
                Modifier.color(AdminColors.Body).fontSize(13.px)
            )
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
            SpanText(
                movie.title,
                Modifier.color(AdminColors.Heading).fontSize(14.px).fontWeight(FontWeight.SemiBold)
            )
        }
        // Genres.
        Cell(220.px) {
            Div(attrs = { style { wrap() } }) {
                movie.genres.take(3).forEach { GenrePill(it.name) }
            }
        }
        // Duration.
        Cell(110.px) {
            SpanText(
                formatDuration(movie.duration),
                Modifier.color(AdminColors.Body).fontSize(14.px)
            )
        }
        // Release date.
        Cell(150.px) { ReleasePill(movie.releasedDate.toString()) }
        // Actions.
        Cell(90.px) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.px),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    { FaPenToSquare(it) },
                    AdminColors.Body
                ) { onAction(AdminMoviesAction.OnEditClick(movie)) }
                IconButton(
                    { FaTrash(it) },
                    AdminColors.Primary
                ) { onAction(AdminMoviesAction.OnDeleteClick(movie)) }
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
            SpanText(
                "Delete movie",
                Modifier.montserrat().color(AdminColors.Heading).fontSize(20.px)
                    .fontWeight(FontWeight.Bold)
            )
            SpanText(
                "Delete \"${movie.title}\"? This can't be undone.",
                Modifier.color(AdminColors.Body).fontSize(14.px),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.px)
            ) {
                Box(modifier = Modifier.flexGrow(1)) {
                    SecondaryButton("Cancel") {
                        onAction(
                            AdminMoviesAction.OnDismissDelete
                        )
                    }
                }
                Box(modifier = Modifier.flexGrow(1)) {
                    PrimaryButton("Delete") {
                        onAction(
                            AdminMoviesAction.OnConfirmDelete
                        )
                    }
                }
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

