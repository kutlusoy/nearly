package com.kutlusoy.nearly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.nostr.BirthDate
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import com.kutlusoy.nearly.ui.registration.SignInForm
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import com.kutlusoy.nearly.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Renders the screens with sample data and writes the pictures to build/screenshots
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = BirthDate(2026, 11, 21)
    private val words = "abandon ability able about above"
    private val account = AccountInfo("anna", "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq", 26)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent { NearlyTheme(dark = dark) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun registration(form: RegistrationForm, busy: Boolean = false) = @Composable {
        RegistrationScreen(form, today, busy, null, {}, {}, {}, {}, {})
    }

    @Test
    fun welcome() = shot("01-welcome") { WelcomeScreen({}, {}) }

    @Test
    fun welcomeDark() = shot("01-welcome-dark", dark = true) { WelcomeScreen({}, {}) }

    @Test
    fun username() = shot("02-registration-username", content = registration(RegistrationForm(username = "anna")))

    @Test
    fun usernameError() = shot("02-registration-username-error", content = registration(RegistrationForm(username = "anna smith")))

    @Test
    fun email() = shot(
        "03-registration-email",
        content = registration(RegistrationForm(step = RegistrationStep.Email, email = "anna@example.org", emailAgain = "anna@exampel.org")),
    )

    @Test
    fun words() = shot("04-registration-words", content = registration(RegistrationForm(step = RegistrationStep.Words, words = words, wordsSaved = true)))

    @Test
    fun wordsDark() = shot("04-registration-words-dark", dark = true, content = registration(RegistrationForm(step = RegistrationStep.Words, words = words)))

    @Test
    fun birthdate() = shot(
        "05-registration-birthdate",
        content = registration(RegistrationForm(step = RegistrationStep.Birthdate, day = "21", month = "11", year = "2000")),
    )

    @Test
    fun birthdateUnderAge() = shot(
        "05-registration-birthdate-under-age",
        content = registration(RegistrationForm(step = RegistrationStep.Birthdate, day = "22", month = "11", year = "2008")),
    )

    @Test
    fun creating() = shot(
        "06-registration-creating",
        content = registration(RegistrationForm(step = RegistrationStep.Birthdate, day = "21", month = "11", year = "2000"), busy = true),
    )

    @Test
    fun signIn() = shot("07-sign-in") { SignInScreen(SignInForm("anna", "anna@example.org", "abandon ability able"), false, null, {}, {}, {}) }

    @Test
    fun signInFailed() = shot("07-sign-in-failed") { SignInScreen(SignInForm("anna", "anna@example.org", words), false, AppError.SignInFailed, {}, {}, {}) }

    @Test
    fun swipeTab() = shot("08-main-swipe") { MainScreen(account, Tab.Swipe, {}, {}) }

    @Test
    fun swipeTabDark() = shot("08-main-swipe-dark", dark = true) { MainScreen(account, Tab.Swipe, {}, {}) }

    @Test
    fun feedTab() = shot("08-main-feed") { MainScreen(account, Tab.Feed, {}, {}) }

    @Test
    fun profileTab() = shot("09-main-profile") { MainScreen(account, Tab.Profile, {}, {}) }

    @Test
    fun profileTabDark() = shot("09-main-profile-dark", dark = true) { MainScreen(account, Tab.Profile, {}, {}) }

    @Test
    fun settings() = shot("10-settings") { SettingsScreen(account, ThemeMode.Light, {}, {}, {}) }

    @Test
    fun settingsDark() = shot("10-settings-dark", dark = true) { SettingsScreen(account, ThemeMode.Dark, {}, {}, {}) }
}
