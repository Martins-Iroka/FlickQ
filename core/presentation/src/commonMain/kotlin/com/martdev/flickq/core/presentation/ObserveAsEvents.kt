package com.martdev.flickq.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Collects one-time [events] from a ViewModel while the composable is present.
 * Used by Root composables for navigation/snackbar side effects.
 */
@Composable
fun <T> ObserveAsEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { onEvent(it) }
        }
    }
}

@Composable
fun <T> ObserveEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    LaunchedEffect(events) {
        events.collectLatest {
            onEvent(it)
        }
    }
}