package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.feed.MemoryFeedStore
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.notify.FakeNotifier
import com.kutlusoy.nearly.notify.NotifySettings
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
class NotifyFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun waitFor(timeoutMs: Long = 180_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    private fun phone(relay: FakeRelay, name: String, notifier: FakeNotifier = FakeNotifier()): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), FakeArea("u33db"), FakePhotoBackend(), { MemoryFeedStore() }, notifier) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    // ben writes to anna, anna follows ben so the message is not a hidden request
    private fun setup(annaNotifier: FakeNotifier, follow: Boolean = true): Pair<AppViewModel, AppViewModel> {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", annaNotifier)
        val ben = phone(relay, "ben")
        waitFor { anna.state.value.nearby.people.size == 1 && ben.state.value.nearby.people.size == 1 }
        if (follow) anna.toggleFollowKey(anna.state.value.nearby.people.single().key!!)
        ben.openChat(ben.state.value.nearby.people.single().key!!)
        return anna to ben
    }

    @Test
    fun aMessageOutsideTheAppShowsTheNameAndTheText() {
        val notifier = FakeNotifier()
        val (_, ben) = setup(notifier)
        ben.sendMessage("See you at 8?")
        waitFor { notifier.messages.isNotEmpty() }
        assertEquals(FakeNotifier.Message(notifier.messages.single().peer, "ben", "See you at 8?", false), notifier.messages.single())
    }

    @Test
    fun withoutTheTextOnlyTheArrivalIsShown() {
        val notifier = FakeNotifier()
        val (anna, ben) = setup(notifier)
        anna.updateNotify(NotifySettings(showText = false))
        ben.sendMessage("secret")
        waitFor { notifier.messages.isNotEmpty() }
        assertEquals(null, notifier.messages.single().name)
        assertEquals(null, notifier.messages.single().text)
    }

    @Test
    fun nothingIsShownWhileTheAppIsOnScreenOrWhenSwitchedOff() {
        val notifier = FakeNotifier()
        val (anna, ben) = setup(notifier)
        anna.appVisible(true)
        ben.sendMessage("one")
        waitFor { anna.state.value.chat.conversations.isNotEmpty() }
        assertTrue(notifier.messages.isEmpty())
        anna.appVisible(false)
        anna.updateNotify(NotifySettings(enabled = false))
        ben.sendMessage("two")
        waitFor { anna.state.value.chat.conversations.single().last.text == "two" }
        assertTrue(notifier.messages.isEmpty())
    }

    @Test
    fun aHiddenRequestMakesNoNoise() {
        val notifier = FakeNotifier()
        val (anna, ben) = setup(notifier, follow = false)
        ben.sendMessage("hello stranger")
        waitFor { anna.state.value.chat.requests.isNotEmpty() }
        assertTrue(notifier.messages.isEmpty())
    }

    @Test
    fun theSettingsDecideIfTheAppKeepsReceiving() {
        val notifier = FakeNotifier()
        val relay = FakeRelay()
        val anna = phone(relay, "anna", notifier)
        assertEquals(true, notifier.background, "on by default after the sign in")
        anna.updateNotify(NotifySettings(background = false))
        assertEquals(false, notifier.background)
        anna.signOut()
        assertEquals(false, notifier.background)
        assertTrue(notifier.clearedAll > 0)
    }

    @Test
    fun aTapOnTheNotificationOpensTheChat() {
        val notifier = FakeNotifier()
        val (anna, ben) = setup(notifier)
        ben.sendMessage("Hi")
        waitFor { notifier.messages.isNotEmpty() }
        val peer = notifier.messages.single().peer
        anna.openFromNotification(peer)
        assertEquals(peer, anna.state.value.chat.openPeer)
        assertTrue(peer in notifier.cleared)
    }

    @Test
    fun theNotificationsScreenSwitchesEverythingAndAsksForThePermission() {
        var settings = NotifySettings()
        var asked = 0
        rule.setContent { NearlyTheme { NotificationsScreen(settings, permitted = false, onChange = { settings = it }, onPermissionResult = { asked++ }, onBack = {}) } }
        rule.onNodeWithTag("notify-permission").assertExists()
        rule.onNodeWithTag("notify-text").performClick()
        assertEquals(false, settings.showText)
        rule.onNodeWithTag("notify-background").performClick()
        assertEquals(false, settings.background)
        rule.onNodeWithTag("notify-enabled").performClick()
        assertEquals(false, settings.enabled)
    }
}
