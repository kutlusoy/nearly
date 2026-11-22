package com.kutlusoy.nearly.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.nearly.chat.ChatController
import com.kutlusoy.nearly.chat.ChatStore
import com.kutlusoy.nearly.chat.MessagePolicy
import com.kutlusoy.nearly.chat.SqliteChatStore
import com.kutlusoy.nearly.discovery.AndroidAreaSource
import com.kutlusoy.nearly.discovery.AreaSource
import com.kutlusoy.nearly.discovery.NearbyController
import com.kutlusoy.nearly.media.BlossomPhotoBackend
import com.kutlusoy.nearly.media.PhotoBackend
import com.kutlusoy.nearly.nostr.DeleteResult
import com.kutlusoy.nearly.nostr.PhotoRef
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
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class AppViewModel @JvmOverloads constructor(
    app: Application,
    box: SecretBox = AndroidKeystoreBox(),
    link: NetworkLink = RelayHub(app),
    area: AreaSource = AndroidAreaSource(app),
    private val photos: PhotoBackend = BlossomPhotoBackend(link, cacheDir = File(app.cacheDir, "photos")),
    chatStoreFor: (String) -> ChatStore = { SqliteChatStore(app, it) },
) : AndroidViewModel(app) {
    private val chat = ChatController(link, chatStoreFor, viewModelScope)
    private val nearby = NearbyController(link, area)
    private val saved = SavedLoginStore(File(app.noBackupFilesDir, "session.bin"), box)
    private val vault = KeyVault(FileKeyStore(File(app.noBackupFilesDir, "accounts")))
    private val prefs = app.getSharedPreferences("nearly", Application.MODE_PRIVATE)
    private var session: Session? = null
    private val pictures = android.util.LruCache<String, ImageBitmap>(48)
    private val downloads = Semaphore(3)

    private val _state = MutableStateFlow(AppState(themeMode = savedTheme(), networkMode = savedNetworkMode(), messagePolicy = savedPolicy(), discovery = DiscoveryFilter.load(prefs), profile = MyProfile.load(prefs), signIn = SignInForm(prefs.getString(KEY_USERNAME, "").orEmpty(), prefs.getString(KEY_EMAIL, "").orEmpty())))
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chat.state.collect { c ->
                _state.update { it.copy(chat = c) }
                nearby.setBlocked(c.blocked.map { b -> b.peer }.toSet())
            }
        }
        viewModelScope.launch { nearby.state.collect { n -> _state.update { it.copy(nearby = n) } } }
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
        chat.open(opened, username, _state.value.profile.about, age, _state.value.networkMode, _state.value.profile.photos.firstOrNull()?.url)
        nearby.open(opened)
        nearby.setRadius(radius(_state.value.discovery))
        publishCard()
    }

    private fun radius(filter: DiscoveryFilter) = if (filter.furtherAway) filter.maxDistanceKm * 2 else filter.maxDistanceKm

    // The own card for swipe and feed: written again after every change of the profile or of the visibility
    private fun publishCard() {
        val s = _state.value
        val account = s.account ?: return
        val p = s.profile
        nearby.publish(
            NearbyController.CardSnapshot(
                name = account.username, age = account.age, gender = p.gender, looking = p.lookingFor,
                languages = p.languages.mapNotNull { NearbyController.languageCode(it) }, job = p.job, city = p.city,
                about = p.about, interests = p.interests, visible = s.discovery.visible, photos = p.photos,
            ),
        )
    }

    fun refreshArea() = nearby.refreshArea()

    fun toggleFollowKey(key: String) {
        val followed = _state.value.chat.following.any { it.peer == key }
        chat.setFollowed(key, !followed)
    }

    fun messagePerson(key: String) = chat.openConversation(key)

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

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab, settingsOpen = false, discoveryOpen = false, editProfileOpen = false, backupOpen = false, followingOpen = false, blockedOpen = false) }
        if (tab == Tab.Swipe || tab == Tab.Feed) nearby.refreshArea()
    }

    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun openEditProfile(open: Boolean) = _state.update { it.copy(editProfileOpen = open) }

    fun updateProfile(profile: MyProfile) {
        MyProfile.save(prefs, profile)
        val before = _state.value.profile
        _state.update { it.copy(profile = profile) }
        publishCard()
        val account = _state.value.account
        if (account != null && (before.about != profile.about || before.photos.firstOrNull() != profile.photos.firstOrNull())) {
            chat.publishProfile(account.username, profile.about, account.age, profile.photos.firstOrNull()?.url)
        }
    }

    // Prepares the photo, uploads it and adds it to the profile. The first photo is the profile picture.
    fun addPhoto(open: () -> InputStream) {
        val opened = session ?: return
        val s = _state.value
        if (s.photoBusy) return
        if (s.profile.photos.size >= MAX_PHOTOS) {
            _state.update { it.copy(photoNote = PhotoNote.TooMany) }
            return
        }
        _state.update { it.copy(photoBusy = true, photoNote = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { photos.upload(opened, open) } }
            result.onSuccess { ref ->
                val p = _state.value.profile
                updateProfile(p.copy(photos = (p.photos + ref).take(MAX_PHOTOS)))
                _state.update { it.copy(photoBusy = false) }
            }.onFailure { _state.update { it.copy(photoBusy = false, photoNote = PhotoNote.UploadFailed) } }
        }
    }

    // The photo leaves the profile and the card at once, then the server is asked to delete the file (BUD-12), which it may refuse
    fun removePhoto(index: Int) {
        val opened = session ?: return
        val p = _state.value.profile
        val ref = p.photos.getOrNull(index) ?: return
        updateProfile(p.copy(photos = p.photos.filterIndexed { i, _ -> i != index }))
        pictures.remove(ref.sha256)
        _state.update { it.copy(photoNote = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { photos.delete(opened, ref) } }.getOrDefault(DeleteResult.Unreachable)
            val note = if (result == DeleteResult.Deleted || result == DeleteResult.Gone) PhotoNote.Removed else PhotoNote.ServerMayKeep
            _state.update { it.copy(photoNote = note) }
        }
    }

    fun makePhotoFirst(index: Int) {
        val p = _state.value.profile
        val ref = p.photos.getOrNull(index) ?: return
        if (index == 0) return
        updateProfile(p.copy(photos = listOf(ref) + p.photos.filterIndexed { i, _ -> i != index }))
    }

    fun clearPhotoNote() = _state.update { it.copy(photoNote = null) }

    // A photo for the screens: from memory, from the disk cache or from the server, never more than three downloads at once
    suspend fun loadPhoto(ref: PhotoRef): ImageBitmap? {
        pictures.get(ref.sha256)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                downloads.withPermit {
                    val bytes = photos.load(ref)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
            bitmap?.also { pictures.put(ref.sha256, it) }
        }
    }

    fun openDiscovery(open: Boolean) = _state.update { it.copy(discoveryOpen = open) }

    fun updateDiscovery(filter: DiscoveryFilter) {
        DiscoveryFilter.save(prefs, filter)
        val visibleChanged = _state.value.discovery.visible != filter.visible
        _state.update { it.copy(discovery = filter) }
        nearby.setRadius(radius(filter))
        if (visibleChanged) publishCard()
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    private fun savedTheme(): ThemeMode = ThemeMode.values().firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.Light

    fun signOut() {
        chat.close()
        nearby.close()
        saved.clear()
        session?.lock()
        session = null
        _state.update { it.copy(screen = Screen.Welcome, account = null, settingsOpen = false, discoveryOpen = false, editProfileOpen = false, backupOpen = false, followingOpen = false, blockedOpen = false, tab = Tab.Swipe, signIn = it.signIn.copy(words = ""), registration = RegistrationForm()) }
    }

    override fun onCleared() {
        chat.close()
        nearby.close()
        session?.lock()
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_AGE = "age"
        const val KEY_THEME = "theme"
        const val KEY_NETWORK = "network.mode"
        const val KEY_POLICY = "message.policy"
    }
}
