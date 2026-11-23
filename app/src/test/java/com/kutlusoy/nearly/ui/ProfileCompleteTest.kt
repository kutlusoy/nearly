package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.nostr.CardCodec
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
class ProfileCompleteTest {
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

    @Test
    fun theEditScreenOffersTheNewFieldsAndWarnsAboutContactData() {
        var profile by mutableStateOf(MyProfile())
        rule.setContent { NearlyTheme { EditProfileScreen(profile, onChange = { profile = it }, onBack = {}) } }
        rule.onNodeWithTag("edit-pronouns").performScrollTo().performTextInput("she/her")
        rule.onNodeWithTag("edit-link").performScrollTo().performTextInput("instagram.com/anna")
        rule.onNodeWithTag("prompt-add").performScrollTo().performClick()
        rule.onNodeWithTag("prompt-answer-0").performScrollTo().performTextInput("Call me on +49 170 1234567")
        rule.waitForIdle()
        assertEquals("she/her", profile.pronouns)
        assertEquals("instagram.com/anna", profile.link)
        assertEquals(1, profile.prompts.size)
        rule.onNodeWithTag("contact-warning").assertExists()
        rule.onNodeWithTag("prompt-question-0").performClick()
        assertEquals(1, profile.prompts[0].q)
        rule.onRoot().captureRoboImage("build/screenshots/30-profile-more.png")
    }

    @Test
    fun aPersonCanBeBlockedFromTheFullView() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { anna.state.value.nearby.people.size == 1 }
        val person = anna.state.value.nearby.people.single()
        var closed = false
        rule.setContent {
            NearlyTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalBlockPerson provides anna::block) {
                    PersonDetail(person, onClose = { closed = true }, onAction = {})
                }
            }
        }
        rule.onNodeWithTag("detail-block").performScrollTo().performClick()
        rule.onNodeWithTag("detail-block-confirm").performClick()
        assertTrue(closed)
        assertTrue(anna.state.value.chat.blocked.any { it.peer == person.key })
        waitFor { anna.state.value.nearby.people.isEmpty() }
    }

    @Test
    fun theFactsAreShownToOthersAndTheCardCarriesThem() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        anna.updateProfile(anna.state.value.profile.copy(about = "Hi", school = "TU", heightCm = 170, pronouns = "she/her", zodiac = Zodiac.Leo, prompts = listOf(ProfilePrompt(0, "Long walks"))))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { ben.state.value.nearby.people.any { it.extras.zodiac == "leo" } }
        val person = ben.state.value.nearby.people.single()
        assertEquals("she/her", person.extras.pronouns)
        assertEquals(170, person.extras.height)
        assertEquals("Long walks", person.extras.prompts.single().a)
        rule.setContent { NearlyTheme { PersonDetail(person, onClose = {}, onAction = {}) } }
        rule.onNodeWithTag("photos").assertExists()
    }

    @Test
    fun hidingAFieldRemovesItFromTheCard() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        anna.updateProfile(anna.state.value.profile.copy(job = "Nurse", pets = "Cat", gender = Gender.Woman))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { ben.state.value.nearby.people.any { it.extras.pets == "Cat" && it.job == "Nurse" } }
        anna.updateProfile(anna.state.value.profile.toggleHidden(ProfileField.Pets).toggleHidden(ProfileField.Job).toggleHidden(ProfileField.Gender))
        waitFor { ben.state.value.nearby.people.any { it.extras.pets.isEmpty() && it.job == null && !it.genderKnown } }
    }

    @Test
    fun aNewPhoneGetsTheProfileBackFromTheCard() {
        val relay = FakeRelay()
        val first = phone(relay, "anna", FakeArea("u33db"))
        first.updateProfile(first.state.value.profile.copy(about = "Back again", job = "Nurse", zodiac = Zodiac.Leo, prompts = listOf(ProfilePrompt(2, "Someone kind"))))
        waitFor { relay.events.any { it.kind == CardCodec.KIND && it.content.contains("Back again") } }
        val words = first.backup()!!.words!!
        first.signOut()
        app.getSharedPreferences("nearly", 0).edit().clear().apply()
        val again = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), FakeArea("u33db")) { MemoryChatStore() }
        again.show(Screen.SignIn)
        again.updateSignIn { it.copy(username = "anna", email = "anna@example.org", words = words) }
        again.submitSignIn()
        waitFor { again.state.value.screen == Screen.Main }
        waitFor { again.state.value.profile.about == "Back again" }
        assertEquals(Zodiac.Leo, again.state.value.profile.zodiac)
        assertEquals("Someone kind", again.state.value.profile.prompts.single().a)
    }

    @Test
    fun deletingTheAccountEmptiesEverythingAndSignsOut() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna", FakeArea("u33db"))
        anna.updateProfile(anna.state.value.profile.copy(about = "Bye"))
        val ben = phone(relay, "ben", FakeArea("u33db"))
        waitFor { ben.state.value.nearby.people.size == 1 }
        anna.deleteAccount()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6))
        waitFor { anna.state.value.screen == Screen.Welcome }
        assertEquals(MyProfile(), anna.state.value.profile)
        assertTrue(relay.events.any { it.kind == 5 }, "a deletion request was sent")
        assertTrue(relay.events.any { it.kind == 0 && it.content == "{}" }, "the public profile was emptied")
        waitFor { ben.state.value.nearby.people.isEmpty() }
    }
}
