package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.nearly.chat.ChatController
import com.kutlusoy.nearly.chat.ChatStore
import com.kutlusoy.nearly.chat.MessagePolicy
import com.kutlusoy.nearly.chat.SqliteChatStore
import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.RelayHub
import com.kutlusoy.nearly.nostr.AccountExistsException
import com.kutlusoy.nearly.nostr.BirthDate
import com.kutlusoy.nearly.nostr.FileKeyStore
import com.kutlusoy.nearly.nostr.AndroidKeystoreBox
import com.kutlusoy.nearly.nostr.KeyVault
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.SavedLogin
import com.kutlusoy.nearly.nostr.SavedLoginStore
import com.kutlusoy.nearly.nostr.SecretBox
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.nostr.WrongPasswordException
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.registration.SignInForm
import com.kutlusoy.nearly.ui.theme.ThemeMode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel @JvmOverloads constructor(
    app: Application,
    box: SecretBox = AndroidKeystoreBox(),
    link: NetworkLink = RelayHub(app),
    chatStoreFor: (String) -> ChatStore = { SqliteChatStore(app, it) },
) : AndroidViewModel(app) {
    private val chat = ChatController(link, chatStoreFor, viewModelScope)
    private val saved = SavedLoginStore(File(app.noBackupFilesDir, "session.bin"), box)
    private val vault = KeyVault(FileKeyStore(File(app.noBackupFilesDir, "accounts")))
    private val prefs = app.getSharedPreferences("nearly", Application.MODE_PRIVATE)
    private var session: Session? = null

    private val _state = MutableStateFlow(AppState(themeMode = savedTheme(), networkMode = savedNetworkMode(), messagePolicy = savedPolicy(), discovery = DiscoveryFilter.load(prefs), profile = MyProfile.load(prefs), following = savedFollowing(), signIn = SignInForm(prefs.getString(KEY_USERNAME, "").orEmpty(), prefs.getString(KEY_EMAIL, "").orEmpty())))
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch { chat.state.collect { c -> _state.update { it.copy(chat = c) } } }
        restore()
    }

    private fun savedNetworkMode(): NetworkMode = NetworkMode.values().firstOrNull { it.name == prefs.getString(KEY_NETWORK, null) && (it == NetworkMode.Tor || it == NetworkMode.Direct) } ?: NetworkMode.Tor

    fun setNetworkMode(mode: NetworkMode) {
        prefs.edit().putString(KEY_NETWORK, mode.name).apply()
        _state.update { it.copy(networkMode = mode) }
        chat.setMode(mode)
    }

    private fun savedPolicy(): MessagePolicy = MessagePolicy.values().firstOrNull { it.name == prefs.getString(KEY_POLICY, null) } ?: MessagePolicy.FollowedOnly

    fun setMessagePolicy(policy: MessagePolicy) {
        prefs.edit().putString(KEY_POLICY, policy.name).apply()
        _state.update { it.copy(messagePolicy = policy) }
        chat.setPolicy(policy)
    }

    fun block(peer: String) = chat.block(peer)

    fun unblock(peer: String) = chat.unblock(peer)

    fun openBlocked(open: Boolean) = _state.update { it.copy(blockedOpen = open) }

    fun followRequest(peer: String) = chat.setFollowed(peer, true)

    fun deleteRequest(peer: String) = chat.deleteConversation(peer)

    // The chat opens with the account: network, inbox and the public profile
    private fun openChat(opened: Session, username: String, age: Int?) {
        chat.setPolicy(_state.value.messagePolicy)
        chat.open(opened, username, _state.value.profile.about, age, _state.value.networkMode)
    }

    fun startChat(input: String) {
        val own = _state.value.account?.npub?.let { runCatching { NostrKeys.toHex(NostrKeys.decodeNpub(it)) }.getOrNull() } ?: return
        chat.startChat(input, own)
    }

    fun openChat(peer: String) = chat.openConversation(peer)

    fun closeChat() = chat.closeConversation()

    fun clearChatError() = chat.clearError()

    fun sendMessage(text: String) = chat.send(text)

    fun retryMessage(id: String) = chat.retry(id)

    fun openFollowing(open: Boolean) = _state.update { it.copy(followingOpen = open) }

    fun followById(input: String) {
        val own = _state.value.account?.npub?.let { runCatching { NostrKeys.toHex(NostrKeys.decodeNpub(it)) }.getOrNull() } ?: return
        chat.followById(input, own)
    }

    fun openProfile(peer: String) = chat.openProfile(peer)

    fun closeProfile() = chat.closeProfile()

    fun messageFromProfile() {
        _state.update { it.copy(followingOpen = false) }
        chat.messageFromProfile()
    }

    fun toggleContactFollow(peer: String) {
        val followed = _state.value.chat.profile?.followed ?: false
        chat.setFollowed(peer, !followed)
    }

    // The user stays signed in until the sign out: the sealed login of this phone opens the account again at the next start
    private fun restore() {
        val login = saved.load() ?: return
        val key = login.secretKey
        try {
            val opened = Session(NostrKeys.publicKey(key), key.copyOf())
            session = opened
            _state.update { it.copy(screen = Screen.Main, account = AccountInfo(login.username, opened.npub, login.age), tab = Tab.Swipe) }
            openChat(opened, login.username, login.age)
        } finally {
            key.fill(0)
        }
    }

    private fun persist(username: String, email: String, words: String, opened: Session, age: Int?) {
        opened.useSecretKey { key -> saved.save(SavedLogin(username.trim(), email.trim(), words.trim(), key.copyOf(), age)) }
    }

    // Read when the backup screen opens and kept only by that screen
    fun backup(): AccountBackup? {
        val opened = session?.takeIf { !it.isLocked } ?: return null
        val login = saved.load()
        val nsec = opened.useSecretKey { NostrKeys.nsec(it) }
        return AccountBackup(
            username = login?.username ?: prefs.getString(KEY_USERNAME, "").orEmpty(),
            email = login?.email ?: prefs.getString(KEY_EMAIL, "").orEmpty(),
            words = login?.words,
            npub = opened.npub,
            nsec = nsec,
        )
    }

    fun openBackup(open: Boolean) = _state.update { it.copy(backupOpen = open) }

    fun today(): BirthDate = LocalDate.now().let { BirthDate(it.year, it.monthValue, it.dayOfMonth) }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun openRegistration() = _state.update {
        it.copy(screen = Screen.Registration, registration = RegistrationForm(words = vault.suggestPassphrase()), error = null)
    }

    fun updateRegistration(change: (RegistrationForm) -> RegistrationForm) = _state.update { it.copy(registration = change(it.registration), error = null) }

    fun rerollWords() = updateRegistration { it.withWords(vault.suggestPassphrase()) }

    fun nextStep() = updateRegistration { it.next(today()) }

    // Leaves the registration at the first step
    fun backStep() = _state.update { s ->
        val previous = s.registration.back()
        if (previous == null) s.copy(screen = Screen.Welcome, error = null) else s.copy(registration = previous, error = null)
    }

    fun updateSignIn(change: (SignInForm) -> SignInForm) = _state.update { it.copy(signIn = change(it.signIn), error = null) }

    fun submitRegistration() {
        val form = _state.value.registration
        val today = today()
        val birthDate = form.birthDate(today)
        if (_state.value.busy || !form.canContinue(today) || !form.isLastStep || birthDate == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.Default) { vault.signUp(form.username, form.email, form.words) } }
            result.onSuccess { opened ->
                session = opened
                val age = birthDate.ageOn(today)
                persist(form.username, form.email, form.words, opened, age)
                prefs.edit().putString(KEY_USERNAME, form.username.trim()).putString(KEY_EMAIL, form.email.trim()).putInt(KEY_AGE, age).apply()
                _state.update { it.copy(busy = false, screen = Screen.Main, account = AccountInfo(form.username.trim(), opened.npub, age), tab = Tab.Profile) }
                openChat(opened, form.username.trim(), age)
            }.onFailure { failure -> _state.update { it.copy(busy = false, error = if (failure is AccountExistsException) AppError.AccountExists else AppError.Generic) } }
        }
    }

    fun submitSignIn() {
        val form = _state.value.signIn
        if (_state.value.busy || !form.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.Default) { vault.signIn(form.username, form.email, form.words) } }
            result.onSuccess { opened ->
                session = opened
                val sameAccount = prefs.getString(KEY_USERNAME, null).equals(form.username.trim(), true)
                val age = if (sameAccount && prefs.contains(KEY_AGE)) prefs.getInt(KEY_AGE, 0) else null
                prefs.edit().putString(KEY_USERNAME, form.username.trim()).putString(KEY_EMAIL, form.email.trim()).apply()
                persist(form.username, form.email, form.words, opened, age)
                _state.update { it.copy(busy = false, screen = Screen.Main, account = AccountInfo(form.username.trim(), opened.npub, age), tab = Tab.Swipe) }
                openChat(opened, form.username.trim(), age)
            }.onFailure { failure -> _state.update { it.copy(busy = false, error = if (failure is WrongPasswordException) AppError.SignInFailed else AppError.Generic) } }
        }
    }

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab, settingsOpen = false, discoveryOpen = false, editProfileOpen = false, backupOpen = false, followingOpen = false, blockedOpen = false) }

    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun openEditProfile(open: Boolean) = _state.update { it.copy(editProfileOpen = open) }

    fun updateProfile(profile: MyProfile) {
        MyProfile.save(prefs, profile)
        _state.update { it.copy(profile = profile) }
    }

    fun toggleFollow(id: String) {
        val next = _state.value.following.let { if (id in it) it - id else it + id }
        prefs.edit().putString(KEY_FOLLOWING, next.joinToString(",")).apply()
        _state.update { it.copy(following = next) }
    }

    private fun savedFollowing(): Set<String> = (prefs.getString(KEY_FOLLOWING, "") ?: "").split(',').filter { it.isNotBlank() }.toSet()

    fun openDiscovery(open: Boolean) = _state.update { it.copy(discoveryOpen = open) }

    fun updateDiscovery(filter: DiscoveryFilter) {
        DiscoveryFilter.save(prefs, filter)
        _state.update { it.copy(discovery = filter) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    private fun savedTheme(): ThemeMode = ThemeMode.values().firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.Light

    fun signOut() {
        chat.close()
        saved.clear()
        session?.lock()
        session = null
        _state.update { it.copy(screen = Screen.Welcome, account = null, settingsOpen = false, discoveryOpen = false, editProfileOpen = false, backupOpen = false, followingOpen = false, blockedOpen = false, tab = Tab.Swipe, signIn = it.signIn.copy(words = ""), registration = RegistrationForm()) }
    }

    override fun onCleared() {
        chat.close()
        session?.lock()
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_AGE = "age"
        const val KEY_THEME = "theme"
        const val KEY_FOLLOWING = "following"
        const val KEY_NETWORK = "network.mode"
        const val KEY_POLICY = "message.policy"
    }
}
