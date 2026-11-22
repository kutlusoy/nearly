package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.ChatError
import com.kutlusoy.nearly.chat.ChatService
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.chat.MessagePolicy
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BlockTest {
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

    private fun signedUp(relay: FakeRelay): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay)) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = "anna", email = "anna@example.org", emailAgain = "anna@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    private fun ownKey(vm: AppViewModel) = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))

    @Test
    fun aBlockedSenderIsDroppedAndCanBeUnblocked() {
        val relay = FakeRelay()
        val vm = signedUp(relay)
        val own = ownKey(vm)
        val spammer = newSession()
        val spam = ChatService(relay, spammer, MemoryChatStore())
        spam.send(own, "Copy it and open it in your browser")
        waitFor { vm.state.value.chat.requests.size == 1 }

        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Chats)
        rule.onNodeWithTag("requests-row").performClick()
        rule.onRoot().captureRoboImage("build/screenshots/21-requests-block.png")
        rule.onNodeWithTag("request-block-${spammer.hex().take(8)}").performClick()
        waitFor { vm.state.value.chat.requests.isEmpty() }
        assertEquals(1, vm.state.value.chat.blocked.size)

        spam.send(own, "Second try")
        val friend = newSession()
        vm.followById(NostrKeys.npub(friend.publicKey))
        vm.closeProfile()
        ChatService(relay, friend, MemoryChatStore()).send(own, "Hi Anna")
        waitFor { vm.state.value.chat.conversations.size == 1 }
        assertTrue(vm.state.value.chat.requests.isEmpty(), "the blocked sender stays out")

        vm.unblock(spammer.hex())
        assertTrue(vm.state.value.chat.blocked.isEmpty())
        spam.send(own, "Third try")
        waitFor { vm.state.value.chat.requests.size == 1 }
    }

    @Test
    fun theProfilePageBlocksAfterAConfirmationAndRemovesTheFollowAndTheChat() {
        val relay = FakeRelay()
        val vm = signedUp(relay)
        val own = ownKey(vm)
        val other = newSession()
        vm.followById(NostrKeys.npub(other.publicKey))
        ChatService(relay, other, MemoryChatStore()).send(own, "Hello")
        waitFor { vm.state.value.chat.conversations.size == 1 }

        rule.setContent { AppRoot(vm) }
        rule.waitForIdle()
        rule.onNodeWithTag("block-contact").performClick()
        rule.onNodeWithTag("block-confirm").assertExists()
        rule.onNodeWithTag("block-confirm").performClick()
        waitFor { vm.state.value.chat.profile == null }
        assertTrue(vm.state.value.chat.conversations.isEmpty())
        assertTrue(vm.state.value.chat.following.isEmpty())
        assertEquals(1, vm.state.value.chat.blocked.size)
    }

    @Test
    fun aBlockedPersonCannotBeStartedAndFollowingUnblocks() {
        val vm = signedUp(FakeRelay())
        val other = newSession()
        vm.followById(NostrKeys.npub(other.publicKey))
        vm.block(other.hex())
        vm.startChat(NostrKeys.npub(other.publicKey))
        assertEquals(ChatError.Blocked, vm.state.value.chat.error)
        vm.clearChatError()
        vm.followById(NostrKeys.npub(other.publicKey))
        assertTrue(vm.state.value.chat.blocked.isEmpty())
    }

    @Test
    fun theSettingsListTheBlockedPeopleAndUnblock() {
        val vm = signedUp(FakeRelay())
        vm.setMessagePolicy(MessagePolicy.FollowedOnly)
        val other = newSession()
        vm.block(other.hex())
        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Profile)
        vm.openSettings(true)
        rule.waitForIdle()
        rule.onNodeWithText("1 blocked").assertExists()
        rule.onNodeWithTag("blocked-row").performClick()
        rule.onRoot().captureRoboImage("build/screenshots/22-blocked.png")
        rule.onNodeWithTag("unblock-${other.hex().take(8)}").performClick()
        waitFor { vm.state.value.chat.blocked.isEmpty() }
        rule.onNodeWithText("You have not blocked anyone.").assertExists()
    }
}
