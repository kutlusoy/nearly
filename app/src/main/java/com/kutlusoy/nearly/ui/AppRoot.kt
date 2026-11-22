package com.kutlusoy.nearly.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val context = LocalContext.current
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    // The system bar icons follow the chosen theme, not the theme of the phone
    SideEffect {
        val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    NearlyTheme(dark = dark) {
        CompositionLocalProvider(LocalPhotoLoader provides viewModel::loadPhoto) {
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
                    val contact = state.chat.profile
                    if (contact != null) {
                        ContactProfileScreen(
                            contact = contact,
                            onBack = viewModel::closeProfile,
                            onMessage = viewModel::messageFromProfile,
                            onToggleFollow = { viewModel.toggleContactFollow(contact.peer) },
                            onBlock = { viewModel.block(contact.peer) },
                            onUnblock = { viewModel.unblock(contact.peer) },
                        )
                    } else if (state.chat.openPeer != null) {
                        ConversationScreen(
                            name = state.chat.openName,
                            messages = state.chat.messages,
                            error = state.chat.error,
                            onSend = viewModel::sendMessage,
                            onRetry = viewModel::retryMessage,
                            onBack = viewModel::closeChat,
                            onProfile = { state.chat.openPeer?.let(viewModel::openProfile) },
                            loadPhoto = viewModel::chatPhoto,
                            onSendPhoto = { uri, once -> viewModel.sendChatPhoto({ context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open") }, once) },
                            onPhotoViewed = viewModel::chatPhotoViewed,
                        )
                    } else if (state.followingOpen) {
                        FollowingScreen(
                            contacts = state.chat.following,
                            error = state.chat.error,
                            onOpen = viewModel::openProfile,
                            onFollowId = viewModel::followById,
                            onClearError = viewModel::clearChatError,
                            onBack = { viewModel.openFollowing(false) },
                        )
                    } else if (state.blockedOpen) {
                        BlockedScreen(
                            contacts = state.chat.blocked,
                            onUnblock = viewModel::unblock,
                            onBack = { viewModel.openBlocked(false) },
                        )
                    } else if (state.discoveryOpen) {
                        DiscoveryScreen(
                            filter = state.discovery,
                            onChange = viewModel::updateDiscovery,
                            onBack = { viewModel.openDiscovery(false) },
                        )
                    } else if (state.backupOpen) {
                        BackupScreen(backup = androidx.compose.runtime.remember { viewModel.backup() }, onBack = { viewModel.openBackup(false) })
                    } else if (state.editProfileOpen) {
                        EditProfileScreen(
                            profile = state.profile,
                            onChange = viewModel::updateProfile,
                            onBack = { viewModel.openEditProfile(false); viewModel.clearPhotoNote() },
                            photoBusy = state.photoBusy,
                            photoNote = state.photoNote,
                            onAddPhoto = viewModel::addPhoto,
                            onRemovePhoto = viewModel::removePhoto,
                            onMakeFirst = viewModel::makePhotoFirst,
                        )
                    } else if (state.settingsOpen) {
                        SettingsScreen(
                            account = account,
                            themeMode = state.themeMode,
                            onTheme = viewModel::setTheme,
                            onBack = { viewModel.openSettings(false) },
                            onSignOut = viewModel::signOut,
                            onDiscovery = { viewModel.openDiscovery(true) },
                            onBackup = { viewModel.openBackup(true) },
                            networkMode = state.networkMode,
                            network = state.chat.network,
                            onNetworkMode = viewModel::setNetworkMode,
                            messagePolicy = state.messagePolicy,
                            onMessagePolicy = viewModel::setMessagePolicy,
                            blockedCount = state.chat.blocked.size,
                            onBlocked = { viewModel.openBlocked(true) },
                        )
                    } else {
                        MainScreen(
                            account = account,
                            tab = state.tab,
                            onTab = viewModel::selectTab,
                            onSettings = { viewModel.openSettings(true) },
                            discovery = state.discovery,
                            onFilters = { viewModel.openDiscovery(true) },
                            profile = state.profile,
                            onEditProfile = { viewModel.openEditProfile(true) },
                            chat = state.chat,
                            onOpenChat = viewModel::openChat,
                            onStartChat = viewModel::startChat,
                            onClearChatError = viewModel::clearChatError,
                            onNetworkSettings = { viewModel.openSettings(true) },
                            onOpenFollowing = { viewModel.openFollowing(true) },
                            onFollowRequest = viewModel::followRequest,
                            onDeleteRequest = viewModel::deleteRequest,
                            onBlockRequest = viewModel::block,
                            nearby = state.nearby,
                            onAllowLocation = viewModel::refreshArea,
                            onFollowKey = viewModel::toggleFollowKey,
                            onMessageKey = viewModel::messagePerson,
                        )
                    }
                }
            }
        }
    }
}
