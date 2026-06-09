package com.martdev.flickq.adminkobweb.koin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import org.koin.core.parameter.ParametersDefinition
import org.koin.mp.KoinPlatform

/**
 * Obtains a reused MVI [ViewModel] from Koin and scopes its lifetime to the calling page.
 *
 * Compose HTML / Kobweb has no `LocalViewModelStoreOwner`, so Koin's `koinViewModel()` isn't
 * available. Instead we resolve the VM from the started Koin, park it in a page-local
 * [ViewModelStore], and clear that store when the page leaves the composition — which invokes
 * the VM's `onCleared()` and cancels its `viewModelScope`, matching navigation-compose behaviour.
 */
@Composable
inline fun <reified VM : ViewModel> rememberAdminViewModel(
    noinline parameters: ParametersDefinition? = null,
): VM {
    val store = remember { ViewModelStore() }
    val vm = remember {
        KoinPlatform.getKoin().get<VM>(parameters = parameters).also { store.put("vm", it) }
    }
    DisposableEffect(Unit) { onDispose { store.clear() } }
    return vm
}
