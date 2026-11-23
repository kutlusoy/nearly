package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.nostr.CardCodec
import com.kutlusoy.nearly.nostr.CardData
import com.kutlusoy.nearly.chat.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileFieldsTest {
    private val prefs get() = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("profile-test", 0)

    private val profile = MyProfile(
        about = "Hi", gender = Gender.Woman, lookingFor = LookingFor.Serious, interests = listOf("Climbing"), languages = setOf("Deutsch"),
        job = "Nurse", school = "Charite", city = "Berlin", heightCm = 170, drinking = Frequency.Sometimes, smoking = Frequency.Never, exercise = Frequency.Often,
        pronouns = "she/her", orientation = Orientation.Bisexual, family = FamilyPlans.Unsure, zodiac = Zodiac.Leo, diet = Diet.Vegan, pets = "Cat",
        link = "instagram.com/anna", prompts = listOf(ProfilePrompt(0, "Sunday"), ProfilePrompt(4, "Coffee")),
    )

    @Test
    fun everyFieldIsKeptOnThePhone() {
        val p = prefs
        MyProfile.save(p, profile.copy(hidden = setOf("pets", "zodiac")))
        assertEquals(profile.copy(hidden = setOf("pets", "zodiac")), MyProfile.load(p))
    }

    @Test
    fun hiddenFieldsAreNotWrittenToTheCard() {
        val shown = profile.cardExtras()
        assertEquals("sometimes", shown.drinking)
        assertEquals(2, shown.prompts.size)
        val hidden = profile.copy(hidden = setOf("drinking", "school", "height", "orientation", "pets", "link", "pronouns", "prompts", "zodiac", "diet", "family", "smoking", "exercise")).cardExtras()
        assertEquals(com.kutlusoy.nearly.nostr.CardExtras(), hidden)
    }

    @Test
    fun anEmptyAnswerNeverLeavesThePhone() {
        val x = profile.copy(prompts = listOf(ProfilePrompt(0, "  "), ProfilePrompt(1, "Real"))).cardExtras()
        assertEquals(listOf(1), x.prompts.map { it.q })
    }

    @Test
    fun theProfileIsBuiltAgainFromTheOwnCard() {
        val extras = profile.cardExtras()
        val event = CardCodec.create(
            newSession(),
            CardData("Anna", 31, "w", "serious", listOf("de"), "Nurse", "Berlin", "Hi", listOf("Climbing"), "u33db", extras = extras),
            1_800_000_000L,
        )
        val card = assertNotNull(CardCodec.parse(event, 1_800_000_000L))
        val back = MyProfile.fromCard(card)
        assertEquals(profile.copy(photos = emptyList()), back)
    }

    @Test
    fun promptsStayWithinTheirRules() {
        var p = MyProfile()
        repeat(5) { p = p.withNewPrompt() }
        assertEquals(PROMPTS_MAX, p.prompts.size)
        assertEquals(3, p.prompts.map { it.q }.distinct().size)
        p = p.withAnswer(0, "line one\nline two" + "x".repeat(300))
        assertTrue(p.prompts[0].a.length <= PROMPT_ANSWER_MAX)
        assertFalse(p.prompts[0].a.contains('\n'))
        val before = p.prompts.map { it.q }
        p = p.withNextQuestion(1)
        assertEquals(3, p.prompts.map { it.q }.distinct().size)
        assertTrue(p.prompts[1].q != before[1])
        assertEquals(2, p.withoutPrompt(0).prompts.size)
    }

    @Test
    fun theLinkKeepsOnlyPlainCharacters() {
        assertEquals("instagram.com/anna_b", MyProfile().withLink("instagram.com/anna_b <> ").link)
        assertEquals("a.com/x", MyProfile().withLink("a.com/x \n<>\"'").link)
        assertEquals(LINK_MAX, MyProfile().withLink("a".repeat(500)).link.length)
    }

    @Test
    fun aPhoneNumberOrAnEmailInTheTextIsRecognised() {
        assertTrue(hasContactData("write me anna@example.org"))
        assertTrue(hasContactData("call +49 170 1234567"))
        assertTrue(hasContactData("0170-12345678"))
        assertFalse(hasContactData("I am 31 and love climbing, 2 dogs"))
        assertFalse(hasContactData("Meet at 18:30"))
    }

    @Test
    fun theHintCountsTheNewFieldsToo() {
        assertEquals(0, MyProfile().completeness())
        assertEquals(100, profile.copy(photos = listOf(com.kutlusoy.nearly.nostr.PhotoRef("https://x/a.jpg", "image/jpeg", "a".repeat(64)))).completeness())
        assertNull(MyProfile().orientation)
    }
}
