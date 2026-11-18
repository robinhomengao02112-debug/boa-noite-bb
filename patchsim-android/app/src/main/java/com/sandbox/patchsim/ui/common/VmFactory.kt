package com.sandbox.patchsim.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sandbox.patchsim.PatchSimApp
import com.sandbox.patchsim.di.ServiceLocator

/** A ViewModelProvider.Factory that constructs feature ViewModels from the
 *  app's [ServiceLocator]. Keeps construction centralized and testable. */
class VmFactory(
    private val services: ServiceLocator,
    private val creators: Map<Class<*>, (ServiceLocator) -> ViewModel>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val creator = creators[modelClass]
            ?: error("Unknown ViewModel class: ${modelClass.name}")
        return creator(services) as T
    }
}

@Composable
inline fun <reified VM : ViewModel> rememberVm(): VM {
    val context = LocalContext.current
    val app = context.applicationContext as PatchSimApp
    return viewModel(factory = VmRegistry.factory(app.services))
}
