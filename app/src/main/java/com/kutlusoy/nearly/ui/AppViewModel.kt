package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.nearly.nostr.AccountExistsException
import com.kutlusoy.nearly.nostr.BirthDate
import com.kutlusoy.nearly.nostr.FileKeyStore
import com.kutlusoy.nearly.nostr.KeyVault
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.nostr.WrongPasswordException
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.registration.SignInForm
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = KeyVault(FileKeyStore(File(app.noBackupFilesDir, "accounts")))
    private val prefs = app.getSharedPreferences("nearly", Application.MODE_PRIVATE)
    private var session: Session? = null

    private val _state = MutableStateFlow(AppState(signIn = SignInForm(prefs.getString(KEY_USERNAME, "").orEmpty(), prefs.getString(KEY_EMAIL, "").orEmpty())))
    val state: StateFlow<AppState> = _state.asStateFlow()

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
                prefs.edit().putString(KEY_USERNAME, form.username.trim()).putString(KEY_EMAIL, form.email.trim()).putInt(KEY_AGE, age).apply()
                _state.update { it.copy(busy = false, screen = Screen.Main, account = AccountInfo(form.username.trim(), opened.npub, age), tab = Tab.Profile) }
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
                _state.update { it.copy(busy = false, screen = Screen.Main, account = AccountInfo(form.username.trim(), opened.npub, age), tab = Tab.Swipe) }
            }.onFailure { failure -> _state.update { it.copy(busy = false, error = if (failure is WrongPasswordException) AppError.SignInFailed else AppError.Generic) } }
        }
    }

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun signOut() {
        session?.lock()
        session = null
        _state.update { it.copy(screen = Screen.Welcome, account = null, signIn = it.signIn.copy(words = ""), registration = RegistrationForm()) }
    }

    override fun onCleared() {
        session?.lock()
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_AGE = "age"
    }
}
