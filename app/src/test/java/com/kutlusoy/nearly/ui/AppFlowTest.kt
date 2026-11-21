package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The whole path of a new user through the screens, with the real account code
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createComposeRule()

    // The account work runs on a background thread and reports back on the main looper, which the test has to let run
    private fun waitFor(timeoutMs: Long = 120_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    private fun start(): AppViewModel {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { NearlyTheme { AppRoot(vm) } }
        return vm
    }

    @Test
    fun aNewUserCreatesAnAccountSignsOutAndSignsInAgain() {
        val vm = start()
        rule.onNodeWithText("Create account").performClick()

        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("Username").performTextInput("anna")
        rule.onNodeWithText("Continue").assertIsEnabled().performClick()

        rule.onNodeWithText("E-mail").performTextInput("anna@example.org")
        rule.onNodeWithText("Repeat e-mail").performTextInput("anna@exampel.org")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("The two e-mail addresses are different.").assertExists()
        vm.updateRegistration { it.copy(emailAgain = "anna@example.org") }
        rule.onNodeWithText("Continue").assertIsEnabled().performClick()

        val words = vm.state.value.registration.words
        assertEquals(5, words.split(" ").size)
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("I wrote them down").performClick()
        rule.onNodeWithText("Continue").assertIsEnabled().performClick()

        vm.updateRegistration { it.copy(day = "21", month = "11", year = "2000") }
        rule.onNodeWithText("Create account").assertIsEnabled().performClick()
        waitFor { vm.state.value.screen == Screen.Main }

        assertEquals("anna", vm.state.value.account?.username)
        assertTrue(vm.state.value.account!!.npub.startsWith("npub1"))
        rule.onNodeWithText("anna").assertExists()
        val npub = vm.state.value.account!!.npub

        rule.onNodeWithText("Sign out").performClick()
        rule.onNodeWithText("Sign in").performClick()
        vm.updateSignIn { it.copy(username = "anna", email = "anna@example.org", words = words) }
        rule.onAllNodesWithText("Sign in").assertCountEquals(2)
        vm.submitSignIn()
        waitFor { vm.state.value.screen == Screen.Main }
        assertEquals(npub, vm.state.value.account?.npub)
    }

    @Test
    fun aWrongWordOnAKnownPhoneIsRefused() {
        val vm = start()
        vm.openRegistration()
        val words = vm.state.value.registration.words
        vm.updateRegistration { it.copy(username = "ben", email = "ben@example.org", emailAgain = "ben@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = com.kutlusoy.nearly.ui.registration.RegistrationStep.Birthdate) }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        vm.signOut()
        vm.updateSignIn { it.copy(username = "ben", email = "ben@example.org", words = "abandon ability able about above") }
        vm.submitSignIn()
        waitFor { !vm.state.value.busy && vm.state.value.error != null }
        assertEquals(AppError.SignInFailed, vm.state.value.error)
        assertTrue(vm.state.value.screen != Screen.Main)
        assertTrue(vm.state.value.account == null)
        // the right words still work
        vm.updateSignIn { it.copy(words = words) }
        vm.submitSignIn()
        waitFor { vm.state.value.screen == Screen.Main }
    }
}
