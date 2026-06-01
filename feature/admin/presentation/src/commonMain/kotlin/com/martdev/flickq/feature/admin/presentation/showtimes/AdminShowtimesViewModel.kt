package com.martdev.flickq.feature.admin.presentation.showtimes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class ShowtimeForm(
    val editingId: Long? = null,
    val movieId: String = "",
    val roomId: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val price: String = "",
    val status: ShowtimeStatus = ShowtimeStatus.SCHEDULED,
) {
    val isValid: Boolean
        get() = (movieId.toLongOrNull() ?: 0) > 0 && (roomId.toLongOrNull() ?: 0) > 0 &&
            startsAt.isInstant() && endsAt.isInstant() && (price.toIntOrNull() ?: -1) >= 0
}

private fun String.isInstant(): Boolean = runCatching { Instant.parse(this) }.isSuccess

data class AdminShowtimesState(
    val isLoading: Boolean = true,
    val showtimes: List<Showtime> = emptyList(),
    val error: UiText? = null,
    val form: ShowtimeForm? = null,
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val deleting: Showtime? = null,
    val statusFor: Showtime? = null,
    val populatingFor: Showtime? = null,
    val message: UiText? = null,
)

sealed interface AdminShowtimesAction {
    data object OnRetry : AdminShowtimesAction
    data object OnAddClick : AdminShowtimesAction
    data class OnEditClick(val showtime: Showtime) : AdminShowtimesAction
    data class OnMovieIdChange(val value: String) : AdminShowtimesAction
    data class OnRoomIdChange(val value: String) : AdminShowtimesAction
    data class OnStartsAtChange(val value: String) : AdminShowtimesAction
    data class OnEndsAtChange(val value: String) : AdminShowtimesAction
    data class OnPriceChange(val value: String) : AdminShowtimesAction
    data object OnSave : AdminShowtimesAction
    data object OnDismissDialog : AdminShowtimesAction
    data class OnDeleteClick(val showtime: Showtime) : AdminShowtimesAction
    data object OnConfirmDelete : AdminShowtimesAction
    data object OnDismissDelete : AdminShowtimesAction
    data class OnStatusClick(val showtime: Showtime) : AdminShowtimesAction
    data class OnStatusPicked(val status: ShowtimeStatus) : AdminShowtimesAction
    data object OnDismissStatus : AdminShowtimesAction
    data class OnPopulateClick(val showtime: Showtime) : AdminShowtimesAction
    data object OnConfirmPopulate : AdminShowtimesAction
    data object OnDismissPopulate : AdminShowtimesAction
    data object OnDismissMessage : AdminShowtimesAction
}

class AdminShowtimesViewModel(
    private val catalog: AdminCatalogRepository,
    private val reservations: AdminReservationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminShowtimesState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminShowtimesAction) {
        when (action) {
            AdminShowtimesAction.OnRetry -> load()
            AdminShowtimesAction.OnAddClick -> _state.update { it.copy(form = ShowtimeForm(), dialogError = null) }
            is AdminShowtimesAction.OnEditClick -> _state.update {
                val s = action.showtime
                it.copy(
                    form = ShowtimeForm(s.id, s.movieId.toString(), s.roomId.toString(), s.startsAt.toString(), s.endsAt.toString(), s.price.toString(), s.status),
                    dialogError = null,
                )
            }
            is AdminShowtimesAction.OnMovieIdChange -> updateForm { it.copy(movieId = action.value.filter { c -> c.isDigit() }) }
            is AdminShowtimesAction.OnRoomIdChange -> updateForm { it.copy(roomId = action.value.filter { c -> c.isDigit() }) }
            is AdminShowtimesAction.OnStartsAtChange -> updateForm { it.copy(startsAt = action.value) }
            is AdminShowtimesAction.OnEndsAtChange -> updateForm { it.copy(endsAt = action.value) }
            is AdminShowtimesAction.OnPriceChange -> updateForm { it.copy(price = action.value.filter { c -> c.isDigit() }) }
            AdminShowtimesAction.OnSave -> save()
            AdminShowtimesAction.OnDismissDialog -> _state.update { it.copy(form = null, dialogError = null) }
            is AdminShowtimesAction.OnDeleteClick -> _state.update { it.copy(deleting = action.showtime) }
            AdminShowtimesAction.OnConfirmDelete -> delete()
            AdminShowtimesAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
            is AdminShowtimesAction.OnStatusClick -> _state.update { it.copy(statusFor = action.showtime) }
            is AdminShowtimesAction.OnStatusPicked -> changeStatus(action.status)
            AdminShowtimesAction.OnDismissStatus -> _state.update { it.copy(statusFor = null) }
            is AdminShowtimesAction.OnPopulateClick -> _state.update { it.copy(populatingFor = action.showtime) }
            AdminShowtimesAction.OnConfirmPopulate -> populate()
            AdminShowtimesAction.OnDismissPopulate -> _state.update { it.copy(populatingFor = null) }
            AdminShowtimesAction.OnDismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private inline fun updateForm(transform: (ShowtimeForm) -> ShowtimeForm) {
        _state.update { it.copy(form = it.form?.let(transform), dialogError = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            catalog.getShowtimes()
                .onSuccess { showtimes -> _state.update { it.copy(isLoading = false, showtimes = showtimes) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUiText()) } }
        }
    }

    private fun save() {
        val form = _state.value.form ?: return
        if (!form.isValid || _state.value.isSaving) return
        val now = Clock.System.now()
        val showtime = Showtime(
            id = form.editingId ?: 0L,
            movieId = form.movieId.toLongOrNull() ?: 0L,
            roomId = form.roomId.toLongOrNull() ?: 0L,
            startsAt = runCatching { Instant.parse(form.startsAt) }.getOrDefault(now),
            endsAt = runCatching { Instant.parse(form.endsAt) }.getOrDefault(now),
            price = form.price.toIntOrNull() ?: 0,
            status = form.status,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result = if (form.editingId == null) catalog.createShowtime(showtime) else catalog.updateShowtime(showtime)
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, form = null) }
                    load()
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, dialogError = error.toUiText()) } }
        }
    }

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = null) }
            catalog.deleteShowtime(target.id)
                .onSuccess { load() }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
        }
    }

    private fun changeStatus(status: ShowtimeStatus) {
        val target = _state.value.statusFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(statusFor = null) }
            catalog.updateShowtimeStatus(target.id, status)
                .onSuccess { load() }
                .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
        }
    }

    private fun populate() {
        val target = _state.value.populatingFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(populatingFor = null) }
            reservations.populateSeats(target.id)
                .onSuccess { _state.update { it.copy(message = UiText.DynamicString("Seats populated for showtime ${target.id}.")) } }
                .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
        }
    }
}
