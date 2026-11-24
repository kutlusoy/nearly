package com.kutlusoy.nearly.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    // Android 13 and later ask before an app may show notifications, once after the sign in
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { viewModel.refreshNotifyPermission() }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(state.screen, state.notify.enabled, state.notifyPermission) {
        if (!asked && state.screen == Screen.Main && state.notify.enabled && !state.notifyPermission && android.os.Build.VERSION.SDK_INT >= 33) {
            asked = true
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // The place is shared only while the app is on screen
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.appVisible(false) else if (event == Lifecycle.Event.ON_START) viewModel.appVisible(true)
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    NearlyTheme(dark = dark) {
        CompositionLocalProvider(LocalPhotoLoader provides viewModel::loadPhoto, LocalBlockPerson provides viewModel::block, LocalReportPerson provides viewModel::report) {
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
                            stranger = state.chat.openStranger,
                            loadPhoto = viewModel::chatPhoto,
                            onSendPhoto = { uri, once, direct, replace -> viewModel.sendChatPhoto({ context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open") }, once, direct, replace) },
                            onCancelPhoto = viewModel::cancelChatPhoto,
                            onPhotoViaRelay = viewModel::sendChatPhotoViaRelay,
                            onPhotoViewed = viewModel::chatPhotoViewed,
                        )
                    } else if (state.composerOpen) {
                        ComposerScreen(
                            busy = state.composeBusy,
                            note = state.composeNote,
                            onBack = { viewModel.openComposer(false) },
                            onSubmit = { text, uris, story -> viewModel.publishPost(text, uris.map { uri -> { context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open") } }, story) },
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
                    } else if (state.notificationsOpen) {
                        NotificationsScreen(
                            settings = state.notify,
                            permitted = state.notifyPermission,
                            onChange = viewModel::updateNotify,
                            onPermissionResult = viewModel::refreshNotifyPermission,
                            onBack = { viewModel.openNotifications(false) },
                        )
                    } else if (state.privacyOpen) {
                        PrivacyScreen(settings = state.presenceSettings, onChange = viewModel::updatePresenceSettings, onBack = { viewModel.openPrivacy(false) })
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
                            onPrivacy = { viewModel.openPrivacy(true) },
                            onNotifications = { viewModel.openNotifications(true) },
                            onDeleteAccount = viewModel::deleteAccount,
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
                            onSwipe = viewModel::swipe,
                            onUndo = viewModel::undoSwipe,
                            feed = state.feed,
                            feedActions = FeedActions(viewModel::likePost, viewModel::unlikePost, viewModel::deletePost, viewModel::retryPost, viewModel::storySeen),
                            onCompose = { viewModel.openComposer(true) },
                            presence = state.presence,
                            presenceSettings = state.presenceSettings,
                            mapActions = MapActions(
                                start = { seconds -> viewModel.startPresence(seconds) },
                                stop = viewModel::stopPresence,
                                explained = viewModel::presenceExplained,
                                privacy = { viewModel.openPrivacy(true) },
                                openProfile = viewModel::openProfile,
                                allowLocation = viewModel::refreshArea,
                            ),
                        )
                    }
                    state.chat.match?.let { match -> MatchDialog(match.name, onMessage = viewModel::messageMatch, onLater = viewModel::dismissMatch) }
                }
            }
        }
    }
}
