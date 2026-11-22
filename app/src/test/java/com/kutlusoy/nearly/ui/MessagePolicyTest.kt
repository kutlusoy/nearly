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
class MessagePolicyTest {
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

    @Test
    fun onlyFollowedPeopleCanWriteByDefaultAndTheChoiceIsKept() {
        val relay = FakeRelay()
        val vm = signedUp(relay)
        assertEquals(MessagePolicy.FollowedOnly, vm.state.value.messagePolicy)
        val stranger = newSession()
        val own = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))
        ChatService(relay, stranger, MemoryChatStore()).send(own, "Hi from a stranger")
        waitFor { vm.state.value.chat.requests.size == 1 }
        assertTrue(vm.state.value.chat.conversations.isEmpty())

        vm.setMessagePolicy(MessagePolicy.Everyone)
        assertEquals(1, vm.state.value.chat.conversations.size)
        assertEquals(1, vm.state.value.chat.unread)
        assertEquals(MessagePolicy.Everyone, AppViewModel(app, SoftwareSecretBox(), FakeLink(relay)) { MemoryChatStore() }.state.value.messagePolicy)
        vm.setMessagePolicy(MessagePolicy.FollowedOnly)
    }

    @Test
    fun withFollowedOnlyStrangersStayHiddenUntilFollowedAndAnswersAreShown() {
        val relay = FakeRelay()
        val vm = signedUp(relay)
        val own = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))

        val spammer = newSession()
        val spam = ChatService(relay, spammer, MemoryChatStore())
        spam.send(own, "Buy cheap watches")
        spam.send(own, "Click here")
        waitFor { vm.state.value.chat.requests.size == 1 }
        assertTrue(vm.state.value.chat.conversations.isEmpty())
        assertEquals(0, vm.state.value.chat.unread, "hidden messages do not make noise")

        val friend = newSession()
        val friendChat = ChatService(relay, friend, MemoryChatStore())
        vm.followById(NostrKeys.npub(friend.publicKey))
        vm.closeProfile()
        friendChat.send(own, "Hi Anna, it is Ben")
        waitFor { vm.state.value.chat.conversations.size == 1 }
        assertEquals(1, vm.state.value.chat.unread)

        val stranger = newSession()
        vm.openChat(stranger.hex())
        vm.sendMessage("Hello, may I ask something?")
        val strangerChat = ChatService(relay, stranger, MemoryChatStore())
        strangerChat.send(own, "Sure, ask")
        waitFor { vm.state.value.chat.messages.size == 2 }
        vm.closeChat()
        assertEquals(2, vm.state.value.chat.conversations.size, "who answers my message is shown")

        vm.followRequest(spammer.hex())
        waitFor { vm.state.value.chat.requests.isEmpty() }
        assertEquals(3, vm.state.value.chat.conversations.size)
        vm.setMessagePolicy(MessagePolicy.Everyone)
    }

    @Test
    fun theChatListPointsToTheRequestsAndTheyCanBeFollowedOrDeleted() {
        val relay = FakeRelay()
        val vm = signedUp(relay)
        val own = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))
        val a = newSession()
        val b = newSession()
        ChatService(relay, a, MemoryChatStore()).send(own, "Spam one")
        ChatService(relay, b, MemoryChatStore()).send(own, "Spam two")
        waitFor { vm.state.value.chat.requests.size == 2 }

        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Chats)
        rule.onNodeWithTag("requests-row").performClick()
        rule.onNodeWithText("Spam one").assertExists()
        rule.onNodeWithText("Spam two").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/20-requests.png")
        rule.onNodeWithTag("request-delete-${b.hex().take(8)}").performClick()
        waitFor { vm.state.value.chat.requests.size == 1 }
        rule.onNodeWithTag("request-follow-${a.hex().take(8)}").performClick()
        waitFor { vm.state.value.chat.conversations.size == 1 }
        assertTrue(vm.state.value.chat.requests.isEmpty())
        vm.setMessagePolicy(MessagePolicy.Everyone)
    }

    @Test
    fun theSettingsOfferBothChoices() {
        val vm = signedUp(FakeRelay())
        vm.setMessagePolicy(MessagePolicy.Everyone)
        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Profile)
        vm.openSettings(true)
        rule.waitForIdle()
        rule.onNodeWithText("Who can write to me").assertExists()
        rule.onNodeWithText("Only people I follow").performClick()
        assertEquals(MessagePolicy.FollowedOnly, vm.state.value.messagePolicy)
        rule.onNodeWithText("Everyone").performClick()
        assertEquals(MessagePolicy.Everyone, vm.state.value.messagePolicy)
    }
}
