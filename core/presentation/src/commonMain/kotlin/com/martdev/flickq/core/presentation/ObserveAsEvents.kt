package com.martdev.flickq.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/**
 * Collects one-time [events] from a ViewModel while the composable is present.
 * Used by Root composables for navigation/snackbar side effects.
 */
@Composable
fun <T> ObserveAsEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    LaunchedEffect(events) {
        events.collect { onEvent(it) }
    }
}
