package com.kutlusoy.nearly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    when (state.screen) {
        Screen.Welcome -> WelcomeScreen(onCreate = viewModel::openRegistration, onSignIn = { viewModel.show(Screen.SignIn) })
        Screen.Registration -> RegistrationScreen(
            form = state.registration,
            today = viewModel.today(),
            busy = state.busy,
            error = state.error,
            onChange = viewModel::updateRegistration,
            onBack = viewModel::backStep,
            onNext = viewModel::nextStep,
            onSubmit = viewModel::submitRegistration,
            onNewWords = viewModel::rerollWords,
        )
        Screen.SignIn -> SignInScreen(
            form = state.signIn,
            busy = state.busy,
            error = state.error,
            onChange = viewModel::updateSignIn,
            onBack = { viewModel.show(Screen.Welcome) },
            onSubmit = viewModel::submitSignIn,
        )
        Screen.Main -> state.account?.let { account ->
            MainScreen(account = account, tab = state.tab, onTab = viewModel::selectTab, onSignOut = viewModel::signOut)
        }
    }
}
