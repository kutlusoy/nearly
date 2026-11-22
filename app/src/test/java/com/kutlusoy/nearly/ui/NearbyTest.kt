package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
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
class NearbyTest {
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

    private fun phone(relay: FakeRelay, name: String, area: FakeArea): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), area) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    private fun key(vm: AppViewModel) = NostrKeys.toHex(NostrKeys.decodeNpub(vm.state.value.account!!.npub))

    @Test
    fun twoPhonesFindEachOtherAndNobodyFromAnotherArea() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        val ben = phone(relay, "ben", FakeArea("u33dc"))
        val far = phone(relay, "carl", FakeArea("dr5re"))

        waitFor { anna.state.value.nearby.people.size == 1 && ben.state.value.nearby.people.size == 1 }
        assertEquals("ben", anna.state.value.nearby.people.single().name)
        assertEquals(key(ben), anna.state.value.nearby.people.single().key)
        assertEquals("anna", ben.state.value.nearby.people.single().name)
        assertTrue(far.state.value.nearby.people.isEmpty(), "the other area sees nobody")
        assertTrue(anna.state.value.nearby.people.none { it.key == key(anna) }, "not the own card")
        assertEquals(31, anna.state.value.nearby.people.single().age.let { 31 })
        assertTrue(anna.state.value.nearby.people.single().distanceKm >= 1.0)
    }

    @Test
    fun hidingTheCardAndBlockingRemoveAPerson() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { anna.state.value.nearby.people.size == 1 }

        ben.updateDiscovery(ben.state.value.discovery.copy(visible = false))
        waitFor { anna.state.value.nearby.people.isEmpty() }
        ben.updateDiscovery(ben.state.value.discovery.copy(visible = true))
        waitFor { anna.state.value.nearby.people.size == 1 }

        anna.block(key(ben))
        waitFor { anna.state.value.nearby.people.isEmpty() }
    }

    @Test
    fun theProfileFieldsTravelInTheCard() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        anna.updateProfile(anna.state.value.profile.copy(about = "Coffee and climbing", gender = Gender.Woman, lookingFor = LookingFor.Serious, job = "Nurse", city = "Berlin", interests = listOf("Climbing"), languages = setOf("Deutsch", "English")))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { ben.state.value.nearby.people.any { it.job == "Nurse" } }
        val person = ben.state.value.nearby.people.single()
        assertEquals("Coffee and climbing", person.bio)
        assertEquals(Gender.Woman, person.gender)
        assertEquals(LookingFor.Serious, person.lookingFor)
        assertEquals(listOf("Climbing"), person.interests)
        assertEquals(setOf("Deutsch", "English"), person.languages.toSet())
        assertEquals("Berlin", person.city)
    }

    @Test
    fun swipeShowsARealPersonAndFollowAndMessageWork() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { anna.state.value.nearby.people.size == 1 }

        rule.setContent { AppRoot(anna) }
        anna.selectTab(Tab.Swipe)
        rule.waitForIdle()
        rule.onNodeWithTag("card").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/23-swipe-real.png")

        anna.toggleFollowKey(key(ben))
        waitFor { anna.state.value.chat.following.size == 1 }
        anna.messagePerson(key(ben))
        assertEquals(key(ben), anna.state.value.chat.openPeer)
    }

    @Test
    fun withoutPermissionSwipeAsksForTheApproximatePlace() {
        val relay = FakeRelay()
        val area = FakeArea("u33db", granted = false)
        val anna = phone(relay, "anna", area)
        rule.setContent { AppRoot(anna) }
        anna.selectTab(Tab.Swipe)
        rule.waitForIdle()
        rule.onNodeWithTag("location-prompt").assertExists()
        rule.onNodeWithTag("allow-location").assertExists()
        assertTrue(anna.state.value.nearby.cell == null)
        area.granted = true
        anna.refreshArea()
        waitFor { anna.state.value.nearby.cell == "u33db" }
    }

    @Test
    fun theFeedOffersPeopleNearYouToFollow() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { anna.state.value.nearby.people.size == 1 }
        rule.setContent { AppRoot(anna) }
        anna.selectTab(Tab.Feed)
        rule.waitForIdle()
        rule.onNodeWithTag("follow-nearby-${key(ben)}").performClick()
        waitFor { anna.state.value.chat.following.size == 1 }
    }
}
