package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.ChatError
import com.kutlusoy.nearly.chat.ChatMessage
import com.kutlusoy.nearly.chat.ChatService
import com.kutlusoy.nearly.chat.ChatUiState
import com.kutlusoy.nearly.chat.ConversationUi
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.chat.MessageStatus
import com.kutlusoy.nearly.chat.SendResult
import com.kutlusoy.nearly.chat.hex
import com.kutlusoy.nearly.chat.newSession
import com.kutlusoy.nearly.network.LinkState
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkStatus
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.ui.registration.RegistrationStep
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = 1_800_000_000L
    private val peerA = "aa".repeat(32)
    private val peerB = "bb".repeat(32)
    private val npub = "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"

    private fun message(id: String, text: String, mine: Boolean, at: Long, status: MessageStatus = if (mine) MessageStatus.Sent else MessageStatus.Received) =
        ChatMessage(id, peerA, mine, text, at, status)

    private fun online() = NetworkStatus(NetworkMode.Tor, LinkState.Online, relaysOpen = 3, relaysTotal = 3)

    @Test
    fun theChatListShowsNamesPreviewsAndUnreadCounts() {
        val chat = ChatUiState(
            conversations = listOf(
                ConversationUi(peerA, "Mia", message("1", "See you at 8?", false, now - 60), 2),
                ConversationUi(peerB, "Jonas", message("2", "Sounds good", true, now - 90_000), 0),
            ),
            unread = 2,
            network = online(),
        )
        rule.setContent { NearlyTheme { ChatsScreen(chat, npub, {}, {}, {}, {}, now) } }
        rule.onNodeWithText("Mia").assertExists()
        rule.onNodeWithText("See you at 8?").assertExists()
        rule.onNodeWithText("2").assertExists()
        rule.onNodeWithText("You: Sounds good").assertExists()
        rule.onNodeWithText("Online, 3 of 3 relays").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/17-chats.png")
    }

    @Test
    fun withoutChatsTheScreenShowsYourIdAndHowToStart() {
        rule.setContent { NearlyTheme { ChatsScreen(ChatUiState(network = online()), npub, {}, {}, {}, {}, now) } }
        rule.onNodeWithText("No chats yet").assertExists()
        rule.onNodeWithTag("my-id").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/17-chats-empty.png")
    }

    @Test
    fun theNetworkBannerSaysWhatIsGoingOn() {
        val cases = listOf(
            NetworkStatus(NetworkMode.Tor, LinkState.StartingTor, torPercent = 45) to "Starting Tor, 45 %",
            NetworkStatus(NetworkMode.Tor, LinkState.TorMissing) to "Tor is not part of this build",
            NetworkStatus(NetworkMode.Direct, LinkState.Connecting) to "Connecting to relays",
        )
        var chat by mutableStateOf(ChatUiState(network = cases[0].first))
        rule.setContent { NearlyTheme { ChatsScreen(chat, npub, {}, {}, {}, {}, now) } }
        for ((status, text) in cases) {
            chat = ChatUiState(network = status)
            rule.waitForIdle()
            rule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun theNewChatScreenChecksTheEntryAndStarts() {
        val started = mutableListOf<String>()
        var chat by mutableStateOf(ChatUiState(network = online()))
        rule.setContent { NearlyTheme { ChatsScreen(chat, npub, {}, { started += it; chat = chat.copy(error = ChatError.BadId) }, { chat = chat.copy(error = null) }, {}, now) } }
        rule.onNodeWithTag("new-chat").performClick()
        rule.onNodeWithTag("newchat-start").assertIsNotEnabled()
        rule.onNodeWithTag("newchat-input").performTextInput("hello")
        rule.onNodeWithTag("newchat-start").assertIsEnabled().performClick()
        assertEquals(listOf("hello"), started)
        rule.onNodeWithText("This is not a valid ID. It starts with npub1.").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/17-new-chat.png")
    }

    @Test
    fun theConversationShowsBubblesWithTheirStateAndSends() {
        val sent = mutableListOf<String>()
        val retried = mutableListOf<String>()
        val messages = listOf(
            message("1", "Hi Anna", false, now - 300),
            message("2", "Hi Mia, nice to meet you", true, now - 240),
            message("3", "Coffee on Saturday?", true, now - 10, MessageStatus.Sending),
            message("4", "This one did not go out", true, now - 5, MessageStatus.Failed),
        )
        rule.setContent { NearlyTheme { ConversationScreen("Mia", messages, null, { sent += it }, { retried += it }, {}, now) } }
        rule.onNodeWithText("Hi Anna").assertExists()
        rule.onNodeWithText("Sending").assertExists()
        rule.onNodeWithText("Not sent. Tap to try again.").assertExists()
        rule.onNodeWithText("This one did not go out").performClick()
        assertEquals(listOf("4"), retried)
        rule.onNodeWithTag("send").assertIsNotEnabled()
        rule.onNodeWithTag("message-input").performTextInput("On my way")
        rule.onNodeWithTag("send").assertIsEnabled().performClick()
        assertEquals(listOf("On my way"), sent)
        rule.onRoot().captureRoboImage("build/screenshots/18-conversation.png")
    }

    @Test
    fun anEmptyConversationInvitesToSayHello() {
        rule.setContent { NearlyTheme { ConversationScreen("Mia", emptyList(), null, {}, {}, {}, now) } }
        rule.onNodeWithText("No messages yet. Say hello.").assertExists()
    }
}

// Two people with the real account code and the real chat service, only the relay is a stand in
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatEndToEndTest {
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
    fun aNewUserWritesToAFriendAndGetsAnAnswerThroughTheScreens() {
        val relay = FakeRelay()
        val link = FakeLink(relay)
        val vm = AppViewModel(app, SoftwareSecretBox(), link) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = "anna", email = "anna@example.org", emailAgain = "anna@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        assertEquals(NetworkMode.Tor, link.startedWith)
        assertTrue(relay.events.any { it.kind == 0 }, "the public profile with the name is published")

        val friend = newSession()
        val friendStore = MemoryChatStore()
        val friendChat = ChatService(relay, friend, friendStore, onIncoming = {})
        friendChat.publishProfile("Ben", "", 31)
        friendChat.start()

        rule.setContent { AppRoot(vm) }
        vm.selectTab(Tab.Chats)
        rule.onNodeWithText("No chats yet").assertExists()
        rule.onNodeWithTag("start-chat").performClick()
        rule.onNodeWithTag("newchat-input").performTextInput(NostrKeys.npub(friend.publicKey))
        rule.onNodeWithTag("newchat-start").performClick()
        waitFor { vm.state.value.chat.openPeer == friend.hex() }
        waitFor { vm.state.value.chat.openName == "Ben" }

        rule.onNodeWithTag("message-input").performTextInput("Hi Ben")
        rule.onNodeWithTag("send").performClick()
        waitFor { friendStore.messages(vm.state.value.account!!.npub.let { runCatching { NostrKeys.toHex(NostrKeys.decodeNpub(it)) }.getOrNull()!! }).isNotEmpty() }
        waitFor { vm.state.value.chat.messages.singleOrNull()?.status == MessageStatus.Sent }
        rule.onNodeWithText("Hi Ben").assertExists()

        val anna = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))
        assertEquals("Hi Ben", friendStore.messages(anna).single().text)
        assertEquals(SendResult.Queued::class, friendChat.send(anna, "Hi Anna!").let { it::class })
        waitFor { vm.state.value.chat.messages.size == 2 }
        waitFor { rule.onAllNodesWithText("Hi Anna!").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, vm.state.value.chat.unread, "the open chat is read at once")

        vm.closeChat()
        waitFor { vm.state.value.chat.openPeer == null }
        rule.onNodeWithText("Ben").assertExists()
        rule.onNodeWithText("Hi Anna!").assertExists()

        friendChat.send(anna, "Are you there?")
        waitFor { vm.state.value.chat.unread == 1 }
        rule.onNodeWithText("Are you there?").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/17-chats-unread.png")

        vm.signOut()
        assertTrue(link.stops >= 1)
        assertEquals(ChatUiState(), vm.state.value.chat)
    }

    @Test
    fun theConnectionCanBeSwitchedAndIsKept() {
        val link = FakeLink(FakeRelay())
        val vm = AppViewModel(app, SoftwareSecretBox(), link) { MemoryChatStore() }
        assertEquals(NetworkMode.Tor, vm.state.value.networkMode)
        vm.setNetworkMode(NetworkMode.Direct)
        assertEquals(NetworkMode.Direct, link.startedWith)
        assertEquals(NetworkMode.Direct, AppViewModel(app, SoftwareSecretBox(), FakeLink(FakeRelay())) { MemoryChatStore() }.state.value.networkMode)
        vm.setNetworkMode(NetworkMode.Tor)
    }
}
