package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.feed.MemoryFeedStore
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.presence.PresenceAudience
import com.kutlusoy.nearly.presence.PresenceSettings
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class PresenceFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun waitFor(timeoutMs: Long = 180_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout at " + Throwable().stackTrace.first { it.className.endsWith("PresenceFlowTest") && it.methodName != "waitFor" } }
            Thread.sleep(50)
        }
    }

    private fun phone(relay: FakeRelay, name: String): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), FakeArea("u33db"), FakePhotoBackend(), { MemoryFeedStore() }) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    @Test
    fun twoFriendsSeeEachOtherOnlyWhileOneIsActive() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna")
        val ben = phone(relay, "ben")
        waitFor { anna.state.value.nearby.people.size == 1 && ben.state.value.nearby.people.size == 1 }
        anna.toggleFollowKey(anna.state.value.nearby.people.single().key!!)
        waitFor { anna.state.value.chat.following.size == 1 && anna.state.value.presence.friendCount == 0 }
        ben.toggleFollowKey(ben.state.value.nearby.people.single().key!!)
        waitFor { anna.state.value.presence.friendCount == 1 && ben.state.value.presence.friendCount == 1 }
        assertTrue(anna.startPresence(900))
        waitFor { anna.state.value.presence.active }
        waitFor { ben.state.value.presence.friends.size == 1 }
        assertEquals("u33dbc", ben.state.value.presence.friends.single().cell)
        assertFalse(ben.state.value.presence.active)
        anna.stopPresence()
        waitFor { !anna.state.value.presence.active }
    }

    @Test
    fun sharingWithNobodyIsRefusedAndStopsAnActivePresence() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna")
        anna.updatePresenceSettings(PresenceSettings(audience = PresenceAudience.Nobody))
        assertFalse(anna.startPresence(900))
        assertEquals(PresenceAudience.Nobody, anna.state.value.presenceSettings.audience)
    }
}
