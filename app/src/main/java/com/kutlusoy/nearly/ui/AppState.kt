package com.kutlusoy.nearly.ui

import com.kutlusoy.nearly.chat.ChatUiState
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.theme.ThemeMode
import com.kutlusoy.nearly.ui.registration.SignInForm

enum class Tab { Swipe, Map, Feed, Chats, Profile }

enum class Screen { Welcome, Registration, SignIn, Main }

enum class AppError { SignInFailed, AccountExists, Generic }

data class AccountInfo(val username: String, val npub: String, val age: Int?)

data class AppState(
    val screen: Screen = Screen.Welcome,
    val registration: RegistrationForm = RegistrationForm(),
    val signIn: SignInForm = SignInForm(),
    val account: AccountInfo? = null,
    val tab: Tab = Tab.Swipe,
    val themeMode: ThemeMode = ThemeMode.Light,
    val settingsOpen: Boolean = false,
    val discoveryOpen: Boolean = false,
    val editProfileOpen: Boolean = false,
    val backupOpen: Boolean = false,
    val followingOpen: Boolean = false,
    val chat: ChatUiState = ChatUiState(),
    val networkMode: NetworkMode = NetworkMode.Tor,
    val profile: MyProfile = MyProfile(),
    val following: Set<String> = emptySet(),
    val discovery: DiscoveryFilter = DiscoveryFilter(),
    val busy: Boolean = false,
    val error: AppError? = null,
)
