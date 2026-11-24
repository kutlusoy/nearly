package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileFeedPhotosTest {
    @get:Rule
    val rule = createComposeRule()

    private val account = AccountInfo("anna", "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq", 26)

    @Test
    fun tappingTheEdgesOfACardFlipsThroughThePhotos() {
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople) } }
        rule.onNodeWithContentDescription("Photo 1 of 4").assertExists()
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.onNodeWithContentDescription("Photo 2 of 4").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/13-swipe-photo-2.png")
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.onNodeWithContentDescription("Photo 4 of 4").assertExists()
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.1f, height * 0.4f)) }
        rule.onNodeWithContentDescription("Photo 3 of 4").assertExists()
    }

    @Test
    fun theFirstPhotoStaysTheFirstWhenGoingBackFurther() {
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople) } }
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.1f, height * 0.4f)) }
        rule.onNodeWithContentDescription("Photo 1 of 4").assertExists()
    }

    @Test
    fun theNextCardStartsAtItsFirstPhoto() {
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople) } }
        rule.onNodeWithTag("card").performTouchInput { click(Offset(width * 0.9f, height * 0.4f)) }
        rule.onNodeWithContentDescription("Skip").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Jonas").assertExists() }.isSuccess }
        rule.onNodeWithContentDescription("Photo 1 of 3").assertExists()
    }

    @Test
    fun everyFieldOfTheProfileCanBeFilledIn() {
        var profile by mutableStateOf(MyProfile())
        rule.setContent { NearlyTheme { EditProfileScreen(profile, { profile = it }, {}) } }

        rule.onNodeWithText("Tell people a little about yourself").performTextInput("Hello there")
        assertEquals("Hello there", profile.about)
        rule.onNodeWithText("Woman").performScrollTo().performClick()
        assertEquals(Gender.Woman, profile.gender)
        rule.onNodeWithText("Woman").performClick()
        assertEquals(null, profile.gender)
        rule.onNodeWithText("Non-binary").performClick()
        assertEquals(Gender.NonBinary, profile.gender)
        rule.onNodeWithText("Something serious").performScrollTo().performClick()
        assertEquals(LookingFor.Serious, profile.lookingFor)
        rule.onNodeWithText("Just for fun").performClick()
        assertEquals(LookingFor.Fun, profile.lookingFor)

        rule.onNodeWithText("Climbing").performScrollTo().performClick()
        rule.onNodeWithText("Add your own").performScrollTo().performTextInput("Pottery")
        rule.onNodeWithText("Add").performClick()
        assertEquals(listOf("Climbing", "Pottery"), profile.interests)
        rule.onNodeWithText("Climbing").performClick()
        assertEquals(listOf("Pottery"), profile.interests)

        rule.onNodeWithText("Deutsch").performScrollTo().performClick()
        rule.onNodeWithText("English").performClick()
        assertEquals(setOf("Deutsch", "English"), profile.languages)

        rule.onNodeWithText("Job").performScrollTo().performTextInput("Nurse")
        rule.onNodeWithText("School").performScrollTo().performTextInput("Uni")
        rule.onNodeWithText("Lives in").performScrollTo().performTextInput("Leipzig")
        rule.onNodeWithText("Height (cm)").performScrollTo().performTextInput("172")
        assertEquals(Triple("Nurse", "Uni", "Leipzig"), Triple(profile.job, profile.school, profile.city))
        assertEquals(172, profile.heightCm)

        rule.onAllNodesWithText("Sometimes")[0].performScrollTo().performClick()
        assertTrue(profile.drinking == Frequency.Sometimes || profile.smoking == Frequency.Sometimes || profile.exercise == Frequency.Sometimes)
    }

    @Test
    fun theProfileTabShowsWhatWasFilledInAndLeadsToTheEditor() {
        var edited = 0
        val filled = MyProfile(about = "Climber and tea lover", lookingFor = LookingFor.Serious, interests = listOf("Tea", "Climbing"), job = "Nurse", city = "Leipzig")
        rule.setContent { NearlyTheme { MainScreen(account, Tab.Profile, {}, {}, profile = filled, chat = com.kutlusoy.nearly.chat.ChatUiState(following = listOf(com.kutlusoy.nearly.chat.ContactUi("a".repeat(64), "A", "", null, "npub", true), com.kutlusoy.nearly.chat.ContactUi("b".repeat(64), "B", "", null, "npub", true))), onEditProfile = { edited++ }) } }
        rule.onNodeWithText("Climber and tea lover").assertExists()
        rule.onNodeWithText("Nurse, Leipzig").assertExists()
        rule.onNodeWithText("Tea").assertExists()
        rule.onNodeWithText("2").assertExists()
        rule.onNodeWithText("Edit profile").performClick()
        assertEquals(1, edited)
        rule.onRoot().captureRoboImage("build/screenshots/14-profile-filled.png")
    }

    @Test
    fun peopleNearbyCanBeFollowedFromTheSuggestions() {
        var following by mutableStateOf(setOf<String>())
        rule.setContent {
            NearlyTheme { FeedScreen(people = SamplePeople, following = following, onFollow = { id -> following = if (id in following) following - id else following + id }) }
        }
        rule.onNodeWithText("People near you").assertExists()
        rule.onNodeWithTag("nearby-mia").assertExists()
        rule.onNodeWithTag("follow-nearby-mia").performClick()
        assertEquals(setOf("mia"), following)
        rule.onNodeWithTag("nearby-mia").assertDoesNotExist()
        rule.onNodeWithTag("follow-nearby-jonas").performClick()
        assertEquals(setOf("mia", "jonas"), following)
        rule.onNodeWithTag("nearby-jonas").assertDoesNotExist()
        rule.onRoot().captureRoboImage("build/screenshots/15-feed-nearby.png")
    }

    @Test
    fun whatWasFilledInIsKept() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val vm = AppViewModel(app)
        val profile = MyProfile(
            about = "Hello", gender = Gender.Man, lookingFor = LookingFor.Open, interests = listOf("Tea", "Pottery"),
            languages = setOf("English", "Dansk"), job = "Chef", school = "Uni", city = "Kiel", heightCm = 181,
            drinking = Frequency.Never, smoking = Frequency.Sometimes, exercise = Frequency.Often,
        )
        vm.updateProfile(profile)
        val again = AppViewModel(app).state.value
        assertEquals(profile, again.profile)
    }
}
