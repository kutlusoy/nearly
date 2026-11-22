package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StaySignedInTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val box = SoftwareSecretBox()

    private fun waitFor(timeoutMs: Long = 120_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    private fun register(vm: AppViewModel, name: String = "anna"): String {
        vm.openRegistration()
        val words = vm.state.value.registration.words
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return words
    }

    @Test
    fun theUserStaysSignedInAfterARestartUntilTheSignOut() {
        val first = AppViewModel(app, box)
        register(first)
        val npub = first.state.value.account!!.npub

        val restarted = AppViewModel(app, box).state.value
        assertEquals(Screen.Main, restarted.screen)
        assertEquals("anna", restarted.account?.username)
        assertEquals(npub, restarted.account?.npub)
        assertEquals(Tab.Swipe, restarted.tab)

        first.signOut()
        assertEquals(Screen.Welcome, AppViewModel(app, box).state.value.screen)
        assertNull(first.backup())
    }

    @Test
    fun signingInAgainAlsoKeepsTheUserSignedIn() {
        val vm = AppViewModel(app, box)
        val words = register(vm)
        vm.signOut()
        vm.updateSignIn { it.copy(username = "anna", email = "anna@example.org", words = words) }
        vm.submitSignIn()
        waitFor { vm.state.value.screen == Screen.Main }
        assertEquals(Screen.Main, AppViewModel(app, box).state.value.screen)
    }

    @Test
    fun withoutTheKeyOfThisPhoneTheUserHasToSignInAgain() {
        register(AppViewModel(app, box))
        assertEquals(Screen.Welcome, AppViewModel(app, SoftwareSecretBox()).state.value.screen)
    }

    @Test
    fun theBackupHoldsEverythingThatBringsTheAccountBack() {
        val vm = AppViewModel(app, box)
        val words = register(vm)
        val backup = assertNotNull(vm.backup())
        assertEquals("anna", backup.username)
        assertEquals("anna@example.org", backup.email)
        assertEquals(words, backup.words)
        assertEquals(vm.state.value.account!!.npub, backup.npub)
        val key = NostrKeys.decodeNsec(backup.nsec)
        assertEquals(backup.npub, NostrKeys.npub(NostrKeys.publicKey(key)))
        assertEquals(words, assertNotNull(AppViewModel(app, box).backup()).words)
    }

    @Test
    fun theSettingsLeadToTheBackupAndTheSecretsStayHiddenUntilAsked() {
        val vm = AppViewModel(app, box)
        val words = register(vm)
        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Profile)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("You stay signed in on this phone until you sign out.").assertExists()
        rule.onNodeWithText("Back up your account").performScrollTo().performClick()
        rule.onNodeWithText("anna@example.org").assertExists()
        rule.onNodeWithText(words).assertDoesNotExist()
        val nsec = vm.backup()!!.nsec
        rule.onNodeWithText(nsec).assertDoesNotExist()
        rule.onNodeWithText("Show secrets").performScrollTo().performClick()
        rule.onNodeWithText(words).assertExists()
        rule.onNodeWithText(nsec).assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/16-backup-revealed.png")
        rule.onNodeWithText("Hide secrets").performScrollTo().performClick()
        rule.onNodeWithText(words).assertDoesNotExist()
    }

    @Test
    fun theBackupScreenSaysWhenTheWordsAreNotOnThePhone() {
        val backup = AccountBackup("anna", "anna@example.org", null, "npub1abc", "nsec1abc")
        rule.setContent { NearlyTheme { BackupScreen(backup, {}) } }
        rule.onNodeWithText("Not stored on this phone. Use the words you wrote down.").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/16-backup-hidden.png")
    }

    @Test
    fun anUnopenedAccountShowsNoBackup() {
        rule.setContent { NearlyTheme { BackupScreen(null, {}) } }
        rule.onNodeWithText("The account is not open. Sign in again to see the backup.").assertExists()
    }
}
