package com.cipherchat.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cipherchat.AppContainer
import com.cipherchat.CipherChatApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as CipherChatApp).container

/** Creates a ViewModel with the app's hand-wired dependencies. State survives configuration changes. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
