package com.kutlusoy.nearly.ui

import com.kutlusoy.nearly.chat.ChatUiState
import com.kutlusoy.nearly.chat.MessagePolicy
import com.kutlusoy.nearly.discovery.NearbyState
import com.kutlusoy.nearly.feed.FeedUiState
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.notify.NotifySettings
import com.kutlusoy.nearly.presence.PresenceSettings
import com.kutlusoy.nearly.presence.PresenceUi
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.theme.ThemeMode
import com.kutlusoy.nearly.ui.registration.SignInForm

enum class Tab { Swipe, Map, Feed, Chats, Profile }

enum class Screen { Welcome, Registration, SignIn, Main }

enum class AppError { SignInFailed, AccountExists, Generic }

// What the photo screen tells the user after an upload or a removal
enum class PhotoNote { TooMany, UploadFailed, Removed, ServerMayKeep }

// What the composer tells the user when a post could not be made
enum class ComposeNote { UploadFailed, Rejected }

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
    val blockedOpen: Boolean = false,
    val chat: ChatUiState = ChatUiState(),
    val nearby: NearbyState = NearbyState(),
    val networkMode: NetworkMode = NetworkMode.Tor,
    val messagePolicy: MessagePolicy = MessagePolicy.FollowedOnly,
    val profile: MyProfile = MyProfile(),
    val discovery: DiscoveryFilter = DiscoveryFilter(),
    val busy: Boolean = false,
    val photoBusy: Boolean = false,
    val photoNote: PhotoNote? = null,
    val feed: FeedUiState = FeedUiState(),
    val composerOpen: Boolean = false,
    val composeBusy: Boolean = false,
    val composeNote: ComposeNote? = null,
    val presence: PresenceUi = PresenceUi(),
    val presenceSettings: PresenceSettings = PresenceSettings(),
    val privacyOpen: Boolean = false,
    val notify: NotifySettings = NotifySettings(),
    val notifyPermission: Boolean = true,
    val notificationsOpen: Boolean = false,
    val error: AppError? = null,
)
