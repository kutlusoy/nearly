package com.kutlusoy.nearly.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import com.kutlusoy.nearly.ui.theme.isDark

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val dark = state.themeMode.isDark()
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    // The system bar icons follow the chosen theme, not the theme of the phone
    SideEffect {
        val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    NearlyTheme(dark = dark) {
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
                if (state.discoveryOpen) {
                    DiscoveryScreen(
                        filter = state.discovery,
                        onChange = viewModel::updateDiscovery,
                        onBack = { viewModel.openDiscovery(false) },
                    )
                } else if (state.settingsOpen) {
                    SettingsScreen(
                        account = account,
                        themeMode = state.themeMode,
                        onTheme = viewModel::setTheme,
                        onBack = { viewModel.openSettings(false) },
                        onSignOut = viewModel::signOut,
                        onDiscovery = { viewModel.openDiscovery(true) },
                    )
                } else {
                    MainScreen(
                        account = account,
                        tab = state.tab,
                        onTab = viewModel::selectTab,
                        onSettings = { viewModel.openSettings(true) },
                        discovery = state.discovery,
                        onFilters = { viewModel.openDiscovery(true) },
                    )
                }
            }
        }
    }
}
