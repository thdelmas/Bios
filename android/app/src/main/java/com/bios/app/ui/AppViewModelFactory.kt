package com.bios.app.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Builds [AppViewModel] with its [AppDependencies]. The graph is constructed
 * lazily inside [create] — which the ViewModel system calls once per
 * ViewModel — so recomposition can hand `viewModel()` a fresh, cheap factory
 * without rebuilding the database, ML model, and adapters each time.
 *
 * Tests don't need this factory: [AppViewModel] now takes its dependencies
 * directly, so a test constructs it with a fake [AppDependencies].
 */
class AppViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AppViewModel(application, ProductionAppDependencies(application)) as T
}

/** Obtains the [AppViewModel] for the current scope, wired via [AppViewModelFactory]. */
@Composable
fun appViewModel(): AppViewModel = viewModel(
    factory = AppViewModelFactory(LocalContext.current.applicationContext as Application),
)
