package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.ChatService
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.chat.hex
import com.kutlusoy.nearly.chat.newSession
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The way from a followed person to the chat: follow with the ID, the profile page with the ID, one tap to write
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ContactsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun waitFor(timeoutMs: Long = 120_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    @Test
    fun aFollowedPersonHasAProfilePageWithTheIdAndAButtonToWrite() {
        val relay = FakeRelay()
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay)) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = "anna", email = "anna@example.org", emailAgain = "anna@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }

        val friend = newSession()
        ChatService(relay, friend, MemoryChatStore()).publishProfile("Ben", "Sailor and baker", 31)
        val friendNpub = NostrKeys.npub(friend.publicKey)

        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Profile)
        rule.onNodeWithTag("following-stat").performClick()
        rule.onNodeWithText("You do not follow anyone yet.", substring = true).assertExists()

        rule.onNodeWithTag("follow-id-input").performTextInput("not an id")
        rule.onNodeWithTag("follow-id-button").performClick()
        rule.onNodeWithText("This is not a valid ID. It starts with npub1.").assertExists()

        rule.onNodeWithTag("follow-id-input").performTextInput("")
        vm.followById(friendNpub)
        waitFor { vm.state.value.chat.profile?.name == "Ben" }
        rule.onNodeWithText("Ben").assertExists()
        rule.onNodeWithText("Sailor and baker").assertExists()
        rule.onNodeWithText("31 years").assertExists()
        rule.onNodeWithTag("contact-npub").assertExists()
        assertEquals(friendNpub, vm.state.value.chat.profile?.npub)
        assertTrue(vm.state.value.chat.profile?.followed == true)
        rule.onRoot().captureRoboImage("build/screenshots/19-contact-profile.png")

        rule.onNodeWithTag("message-contact").performClick()
        waitFor { vm.state.value.chat.openPeer == friend.hex() }
        assertTrue(vm.state.value.chat.profile == null)
        assertTrue(!vm.state.value.followingOpen)

        rule.onNodeWithTag("conversation-header").performClick()
        waitFor { vm.state.value.chat.profile != null }
        rule.onNodeWithTag("contact-npub").assertExists()
        rule.onNodeWithTag("follow-contact").performClick()
        waitFor { vm.state.value.chat.profile?.followed == false }
        rule.onNodeWithTag("follow-contact").performClick()
        waitFor { vm.state.value.chat.profile?.followed == true }
    }

    @Test
    fun theNewChatScreenListsThePeopleYouFollowAndStartsTheChatWithOneTap() {
        val relay = FakeRelay()
        val store = MemoryChatStore()
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay)) { store }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = "anna", email = "anna@example.org", emailAgain = "anna@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        val friend = newSession()
        ChatService(relay, friend, MemoryChatStore()).publishProfile("Ben", "", 31)
        vm.followById(NostrKeys.npub(friend.publicKey))
        waitFor { vm.state.value.chat.following.any { it.name == "Ben" } }
        vm.closeProfile()

        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Chats)
        rule.onNodeWithTag("new-chat").performClick()
        rule.onNodeWithText("People you follow").assertExists()
        rule.onNodeWithTag("contact-${friend.hex().take(8)}").performClick()
        waitFor { vm.state.value.chat.openPeer == friend.hex() }
        assertEquals(1, vm.state.value.chat.following.size)
    }
}
